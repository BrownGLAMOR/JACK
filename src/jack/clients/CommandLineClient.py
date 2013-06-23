#!/usr/bin/python

import cmd
import contextlib
import threading
import socket
import sys
import time

class CommandLineClient(cmd.Cmd):
    prompt = '<<<< '
    def __init__(self, sock):
        cmd.Cmd.__init__(self)
        self.sock = sock
    def default(self, line):
        self.sock.send(line + '\n')

class Receiver(threading.Thread):
    def __init__(self, sock):
        threading.Thread.__init__(self)
        self.daemon = True
        self.sock = sock
    def run(self):
        with contextlib.closing(self.sock.makefile()) as f:
            for message in f:
                print '\r>>>>', message.rstrip()

def main(argv):
    host = argv[1] if len(argv) > 1 else '127.0.0.1'
    port = int(argv[2]) if len(argv) > 2 else 1300

    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect((host, port))

    print '==== Connected to', host + ':' + str(port), '===='

    receiver = Receiver(sock)
    sender = CommandLineClient(sock)

    receiver.start()
    sender.cmdloop()

if __name__ == '__main__':
    main(sys.argv)
