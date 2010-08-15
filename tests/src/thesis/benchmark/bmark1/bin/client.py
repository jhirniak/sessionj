#!/usr/bin/env python

##
# tests/src/thesis/benchmark/bmark1/bin/client.py false 7777 localhost 8888 SJm 2 3 BODY
# nohup tests/src/thesis/benchmark/bmark1/bin/client.py false 7777 localhost 8888 SJm 2 3 BODY < /dev/null 1>foo.txt 2>bar.txt &
##	

import os
import socket
import sys

import common


##
# Main execution command.
##
renv = "bin/sessionj -J " + common.JAVA # Uses client JVM by default
	

##
# Command line arguments.
##
if len(sys.argv) != 9:
	common.runtime_error('Usage: client.py <debug> <client_port> <serverName> <server_port> <version> <repeats> <iters> <timer>')
debug      = common.parse_boolean(sys.argv[1])
cport      = int(sys.argv[2]) # Client port
serverName = sys.argv[3]
sport      = sys.argv[4]      # Server port
version    = sys.argv[5]
repeats    = int(sys.argv[6])
iters      = sys.argv[7]      # Inner iterations per Server and Client instance 
timer      = sys.argv[8]      # Timer mode: e.g. FULL, BODY, etc.


##
# Benchmark configuration parameters.
##
if version == 'ALL':
	versions = common.ALL_VERSIONS				
else:
	versions = [version]

if debug:
	(message_sizes, session_lengths) = common.get_debug_parameters()
else:
	(message_sizes, session_lengths) = common.get_parameters()


##
# Run one Client instance.
##
kill_command = renv + ' -cp tests/classes thesis.benchmark.bmark1.SignalClient ' + str(debug) + ' ' + serverName + ' ' + sport + ' KILL'

def run_client(debug, s, run_command):								
	s.recv(1024) # Wait for the Server to signal that it is ready				
	common.debug_print(debug, 'Command: ' + run_command)	
	os.system(run_command)								
	common.debug_print(debug, 'Command: ' + kill_command)	
	os.system(kill_command)


##
# Main.
##
common.print_and_flush('Global: renv=' + renv + ', timer=' + timer + ', versions=' + str(versions) + ', message_sizes=' + str(message_sizes) + ', session_lengths=' + str(session_lengths))

server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_socket.bind((socket.gethostname(), cport))
server_socket.listen(5) # 5 seems to be a kind of default.
common.debug_print(debug, 'Listening on port: ' + str(cport))	
(s, address) = server_socket.accept()

for v in versions:
	transport = ''
	if v == 'RMI':
		client = 'rmi.RMIClient'
	elif v.startswith('SJ'):		
		transport = v[2]
		v = v[0:2]		
		client = 'sj.SJClient'
	elif v == 'SOCKET':
		client = 'socket.SocketClient'
	else:
		common.runtime_error('Bad flag: ' + v)
	
	for size in message_sizes:
		for length in session_lengths:
			runCommand = renv		
			if debug:
				runCommand += ' -V'																			
			if transport != '':
				runCommand += ' -Dsessionj.transports.negotiation=' + transport \
			              + ' -Dsessionj.transports.session=' + transport
			runCommand += ' -cp tests/classes thesis.benchmark.bmark1.' \
			            + client \
			            + ' ' + str(debug) \
			            + ' ' + serverName \
			            + ' ' + sport \
			            + ' -1 ' \
			            + size \
			            + ' ' + length \
			            + ' ' + iters \
			            + ' ' + timer		
		
			for i in range(0, repeats): # Number of Server and Client instances to repeat (cf. iters)
				common.print_and_flush('Parameters: version=' + v + transport + ', size=' + size + ', length=' + length + ', repeat=' + str(i))	
				run_client(debug, s, runCommand)								
				