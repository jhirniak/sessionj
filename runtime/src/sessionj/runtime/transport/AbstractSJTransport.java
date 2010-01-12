package sessionj.runtime.transport;

import sessionj.runtime.net.SJServerSocket;
import sessionj.runtime.net.SJSocket;
import sessionj.runtime.net.TransportSelector;

public abstract class AbstractSJTransport implements SJTransport {
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object o) {
	    return o instanceof SJTransport 
		    && getTransportName().equals(((SJTransport) o).getTransportName());
    }

    /**
     * Default implementation if async mode is unsupported.
     * @return null, always
     */
    public TransportSelector transportSelector() {
        return new TransportSelector() {
	        public boolean registerAccept(SJServerSocket ss) {
		        return false;
	        }

	        public boolean registerInput(SJSocket s) {
		        return false;
	        }

	        public SJSocket select(boolean considerSessionType) {
		        throw new UnsupportedOperationException("Dummy Transport Selector");
	        }

	        public void close() {
		        throw new UnsupportedOperationException("Dummy Transport Selector");
	        }
        };
    }

    public boolean supportsBlocking() {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
