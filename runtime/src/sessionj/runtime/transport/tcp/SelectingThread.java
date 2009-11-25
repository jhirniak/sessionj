package sessionj.runtime.transport.tcp;

import sessionj.runtime.session.OngoingRead;
import sessionj.runtime.session.SJDeserializer;
import static sessionj.runtime.transport.tcp.SelectingThread.ChangeAction.*;
import sessionj.runtime.SJIOException;
import sessionj.util.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import static java.nio.channels.SelectionKey.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SelectingThread implements Runnable {
    private static final int BUFFER_SIZE = 16384;
    private final Selector selector;
    private final ByteBuffer readBuffer;

    private final Queue<ChangeRequest> pendingChangeRequests;

    private final ConcurrentHashMap<SocketChannel, BlockingQueue<ByteBuffer>> readyInputs;
    private final BlockingQueue<Object> readyForSelect;
    private final ConcurrentHashMap<ServerSocketChannel, BlockingQueue<SocketChannel>> accepted;
    private final ConcurrentHashMap<SocketChannel, Queue<ByteBuffer>> requestedOutputs;
    private static final Logger logger = Logger.getLogger(SelectingThread.class.getName());
    private final Map<SocketChannel, SJDeserializer> deserializers;

    SelectingThread() throws IOException {
        selector = SelectorProvider.provider().openSelector();
        pendingChangeRequests = new ConcurrentLinkedQueue<ChangeRequest>();
        readyInputs = new ConcurrentHashMap<SocketChannel, BlockingQueue<ByteBuffer>>();
        requestedOutputs = new ConcurrentHashMap<SocketChannel, Queue<ByteBuffer>>();
        readyForSelect = new LinkedBlockingQueue<Object>();
        accepted = new ConcurrentHashMap<ServerSocketChannel, BlockingQueue<SocketChannel>>();
        deserializers = new HashMap<SocketChannel, SJDeserializer>();
        readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    }

    synchronized void registerAccept(ServerSocketChannel ssc) {
        assert !accepted.containsKey(ssc) : "The channel " + ssc + " has already been registered";
        accepted.put(ssc, new LinkedBlockingQueue<SocketChannel>());
        pendingChangeRequests.add(new ChangeRequest(ssc, REGISTER, OP_ACCEPT));
        // Don't touch selector registrations here, do all
        // selector handling in the selecting thread (as selector keys are not thread-safe)
        
        selector.wakeup(); 
        // Essential for the first selector registration:
        // before that, the thread is blocked on select with no channel registered,
        // hence sleeping forever.
    }

    synchronized void registerInput(SocketChannel sc, SJDeserializer deserializer) {
        assert !readyInputs.containsKey(sc) : "The channel " + sc + " has already been registered";
        logger.finer("Registering for input: " + sc + ", deserializer: " + deserializer);
        readyInputs.put(sc, new LinkedBlockingQueue<ByteBuffer>());
        deserializers.put(sc, deserializer);
        // Don't change the order of these 2 statements: it's safe if we're interrupted after the first
        // one, as the channel is not registered with the selector yet. But if the registration requst is
        // added first, could have a NullPointerException
        pendingChangeRequests.add(new ChangeRequest(sc, REGISTER, OP_READ));
        // Don't touch selector registrations here, do all
        // selector handling in the selecting thread (as selector keys are not thread-safe)
        selector.wakeup();
    }
    
    synchronized void deregisterInput(SocketChannel sc) {
        readyInputs.remove(sc);
        pendingChangeRequests.add(new ChangeRequest(sc, CANCEL, -1));
        selector.wakeup();
    }


    /**
     * Non-blocking, as it should be used right after a select
     * call.
     * @param sc The channel for which to get an input message.
     * @return A complete message (according to the OngoingRead instance obtained from the serializer)
     * @throws java.util.NoSuchElementException if no message is ready for reading.
     */
    public ByteBuffer dequeueFromInputQueue(SocketChannel sc) {
        logger.finer("Dequeueing input from channel: " + sc);
        return readyInputs.get(sc).remove();
    }
    
    public ByteBuffer peekAtInputQueue(SocketChannel sc) {
        logger.finer("Peeking at inputs for channel: " + sc);
        return readyInputs.get(sc).peek();
    }

    public synchronized void enqueueOutput(SocketChannel sc, byte[] bs) {
        Queue<ByteBuffer> outputsForChan = requestedOutputs.get(sc);
        if (outputsForChan == null) {
            outputsForChan = new ConcurrentLinkedQueue<ByteBuffer>();
            requestedOutputs.put(sc, outputsForChan);
        }
        outputsForChan.add(ByteBuffer.wrap(bs));
        logger.finer("Enqueued write on: " + sc + " of: " + bs.length + " bytes");
        pendingChangeRequests.add(new ChangeRequest(sc, CHANGEOPS, OP_WRITE));
        selector.wakeup();
    }

    public void enqueueOutput(SocketChannel sc, byte b) {
        enqueueOutput(sc, new byte[]{b});
    }

    public void close(SelectableChannel sc) {
        // ConcurrentLinkedQueue - no synchronization needed
        pendingChangeRequests.add(new ChangeRequest(sc, CLOSE, -1));
    }

    public SocketChannel takeAccept(ServerSocketChannel ssc) throws InterruptedException {
        // ConcurrentHashMap and LinkedBlockingQueue, both thread-safe,
        // and no need for atomicity here
        BlockingQueue<SocketChannel> queue = accepted.get(ssc);
        logger.finer("Waiting for accept on server socket: " + ssc + " in queue: " + queue);
        return queue.take();
    }

    public void notifyAccepted(ServerSocketChannel ssc, SocketChannel socketChannel) {
        readyForSelect.add(new Pair<ServerSocketChannel, SocketChannel>(ssc, socketChannel));
    }

    public Object dequeueChannelForSelect() throws InterruptedException {
        return readyForSelect.take();
    }

    public void run() {
        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                updateRegistrations();
                doSelect();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error in selecting loop", e);
            }
        }
    }

    private void updateRegistrations() throws IOException {
        while (!pendingChangeRequests.isEmpty()) {
            ChangeRequest req = pendingChangeRequests.remove();
            req.execute(selector);
        }
    }

    private void doSelect() throws IOException {
        logger.finer("About to call select()");
        selector.select();
        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey key = it.next();
            // This seems important: without it, we get notified several times
            // for the same event, resulting in eg. NPEs on accept.
            it.remove();

            if (key.isValid()) {
                if (key.isAcceptable()) {
                    accept(key);
                } else if (key.isReadable()) {
                    read(key);
                } else if (key.isWritable()) {
                    write(key);
                } else { // isConnectable
                    assert false : "Should not get here: readyOps = " + key.readyOps();
                }
            }
        }
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Queue<ByteBuffer> queue = requestedOutputs.get(socketChannel);

        boolean writtenInFull = true;
        logger.finer("Writing data on: " + socketChannel);
        // Write until there's no more data, or the socket's buffer fills up
        while (!queue.isEmpty() && writtenInFull) {
            ByteBuffer buf = queue.peek();
            socketChannel.write(buf);
            writtenInFull = buf.remaining() == 0;
            if (writtenInFull) queue.remove();
        }

        if (writtenInFull) {
            // We wrote away all data, so we're no longer interested
            // in writing on this socket. Switch back to waiting for data.
            key.interestOps(OP_READ);
        }
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        logger.finer("Reading on: " + socketChannel);
        // Clear out our read buffer so it's ready for new data
        readBuffer.clear();

        // Attempt to read off the channel
        int numRead;
        try {
            numRead = socketChannel.read(readBuffer);
        } catch (IOException e) {
            logger.log(Level.WARNING, 
                "Remote peer forcibly closed connection, closing channel and cancelling key", e);
            key.cancel();
            socketChannel.close();
            return;
        }

        // socketChannel.read() advances the position, so need to set it back to 0.
        // also set the limit to the previous position, so that the next
        // method will not try to read past what was read from the socket.
        readBuffer.flip();

        try {
            consumeBytesRead(key, (SocketChannel) key.channel(),
                    readBuffer.asReadOnlyBuffer(), // just to be safe (shouldn't hurt speed)
                    numRead == -1 // -1 if and only if eof
            );
        } catch (SJIOException e) {
            logger.log(Level.SEVERE, "Could not deserialize on channel: " + key.channel(), e);
        }

        if (numRead == -1) {
            // Remote entity shut the socket down cleanly. Do the
            // same from our end and cancel the channel.
            key.cancel();
            socketChannel.close();
        }

    }

    private void consumeBytesRead(SelectionKey key, SocketChannel sc, ByteBuffer bytes, boolean eof) throws SJIOException {
        OngoingRead read = (OngoingRead) key.attachment();
        while (bytes.remaining() > 0) {
            if (read == null) read = attachNewOngoingRead(key);
            
            read.updatePendingInput(bytes, eof);
            
            if (read.finished()) {
                ByteBuffer input = read.getCompleteInput();
                logger.finer("Received complete input on channel " + sc + ": " + input);
                if (readyInputs.containsKey(sc)) {
                    readyInputs.get(sc).add(input);
                    // order is important here: adding to readyForSelect makes the read visible to select
                    readyForSelect.add(sc);
                } else {
                    logger.finer("Dropping input received on deregistered channel: " + sc + ", input: " + input);
                }
                
                read = attachNewOngoingRead(key);
            }
        }
    }

    private OngoingRead attachNewOngoingRead(SelectionKey key) throws SJIOException {
        OngoingRead read = deserializers.get(key.channel()).newOngoingRead();
        key.attach(read);
        return read;
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = ssc.accept();
        socketChannel.configureBlocking(false);
        BlockingQueue<SocketChannel> queue = accepted.get(ssc);
        logger.finer("Enqueuing accepted socket for server socket: " + ssc + " in queue: " + queue);
        queue.add(socketChannel);
    }

    public ByteBuffer takeFromInputQueue(SocketChannel sc) throws InterruptedException {
        return readyInputs.get(sc).take();
    }

    private static class ChangeRequest {
        private final SelectableChannel chan;
        private final ChangeAction changeAction;
        private final int interestOps;

        ChangeRequest(SelectableChannel chan, ChangeAction changeAction, int interestOps) {
            assert chan != null;
            assert changeAction != null;
            this.chan = chan;
            this.changeAction = changeAction;
            this.interestOps = interestOps;
        }

        void execute(Selector selector) throws IOException {
            changeAction.execute(selector, this);
        }
    }

    enum ChangeAction {
        REGISTER {
            void execute(Selector selector, ChangeRequest req) throws ClosedChannelException {
                req.chan.register(selector, req.interestOps);
            }
        }, CHANGEOPS {
            void execute(Selector selector, ChangeRequest req) {
                logger.finer("Changing ops for: " + req.chan + " to: " + req.interestOps);
                SelectionKey key = req.chan.keyFor(selector);
                if (key != null && key.isValid()) key.interestOps(req.interestOps);
            }
        }, CLOSE {
            void execute(Selector selector, ChangeRequest req) throws IOException {
                // Implicitly cancels all existing selection keys for that channel (see javadoc).
                req.chan.close();
            }
        }, CANCEL {
            void execute(Selector selector, ChangeRequest req) {
                SelectionKey key = req.chan.keyFor(selector);
                if (key != null) key.cancel();
            }};
        abstract void execute(Selector selector, ChangeRequest req) throws IOException;
    }

}
