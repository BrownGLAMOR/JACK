#!/usr/bin/python

from contextlib import closing
import logging
from random import choice, randint
import sys
import socket
from threading import Thread, Event
from time import sleep

# The below includes are necessary in order to use our models. This is
# effectively what the django shell does to setup its environment. I believe
# that there is a simplier way to do this in newer versions of django.

from django.core.management import setup_environ

# Get the jackweb settings

sys.path.append('../..')
from jackweb import settings
setup_environ(settings)

from django.db.utils import IntegrityError
from django.contrib.sessions.models import Session

# Finally import our ffad models!

from ffad.models import POSITIONS, Draft, Manager, Player, Bid

def debug(message):
    print message
    #logging.debug(message)

class Sender(Thread):
    def __init__(self, sock):
        Thread.__init__(self)
        self._stop = Event()
        self._sock = sock

    def stop(self):
        debug('Sender: received stop signal')
        self._stop.set()

    def run(self):
        debug('Sender: starting...')
        while not self._stop.isSet():
            managers = Manager.objects.filter(verified=False)
            for manager in managers:
                self._send_bidder(manager)
            bids = Bid.objects.filter(processed=False)
            for bid in bids:
                self._send_bid(bid)
                bid.processed = True
                bid.save()
            sleep(0.1)
        debug('Sender: stopped')

    def _send_bidder(self, manager):
        #message = 'bidder sessionId=' + str(manager.draft.id) + \
        #          ' bidderId=' + manager.name
        #debug('Sender: sending message: ' + message)
        #self._sock.send(message + '\n');
        manager.verified=True
        manager.save()

    def _send_bid(self, bid):
        message = 'bid sessionId=' + str(bid.manager.draft.id) + \
                  ' auctionId=' + str(bid.player.auction_id) + \
                  ' bidderId=' + str(bid.manager.id) + \
                  ' bid=' + str(bid.value)
        debug('Sender: sending message: ' + message)
        self._sock.send(message + '\n');

class Receiver():
    def __init__(self, sock):
        self._stop = Event()
        self._sock = sock
        self._handlers = {
            'bidder': self._handle_bidder,
            'auction': self._handle_auction,
            'start': self._handle_start,
            'status': self._handle_status,
            'stop': self._handle_stop
        }

    def start(self):
        debug('Receiver: starting...')
        with closing(self._sock.makefile()) as f:
            for message in f:
                debug('Receiver: received message: ' + message)
                try:
                    command, keyvals = message.split(None, 1)
                    args = dict(keyval.split('=',1) for keyval in keyvals.split())
                    handler = self._handlers[command]
                except ValueError:
                    debug('WARNING: Missing arguments to message')
                    continue
                except KeyError:
                    debug('WARNING: Unknown message type: ' + command)
                    continue
                try:
                    handler(args)
                except Exception as e:
                    debug('ERROR: Failed to handle message')
                    debug(e)
        debug('Receiver: stopped')

    def _handle_bidder(self, args):
        """ This function processes the bidder notification message from the Jack
        server. It signals the participation of a new biider/manager in the
        specified draft. To do this we mark an existing manager as verified, ro
        add a new one.
        """

        draft_id = args['sessionId']
        name = args['bidderId']
        budget = int(args['budget'])

        draft = Draft.objects.get(id=draft_id)
        manager, _  = Manager.objects.get_or_create(draft=draft, name=name);
        manager.budget = budget
        manager.verified = True
        manager.save()

    def _handle_auction(self, args):
        """ This function processes the auction notification message from the Jack
        server. It alerts the participants to a new auction in the schedule.
        """

        draft_id = args['sessionId']
        auction_id = args['auctionId']
        order = int(args['order'])
        name = args['name']
        position = args['position'] # Validated on save
        value = int(args['estValue'])

        draft = Draft.objects.get(id=draft_id)
        player = Player(draft=draft,
                        auction_id=auction_id,
                        order=order,
                        name=name,
                        position=position,
                        value=value)
        player.save()

    def _handle_start(self, args):
        """ This function processes the start auction message from the Jack server.
        In terms of the Fantasy Football Auction Draft, auctions are equivalent to
        players. An auction is considered to be in progress when its timer is a
        positive integer.
        """

        draft_id = args['sessionId']
        auction_id = args['auctionId']
        timer = int(args['timer'])

        # Get the player whose auction is starting. If the player does not exist
        # then create him with random default values.
        # TODO: Default values should probably come from the message itself

        draft = Draft.objects.get(id=draft_id)
        player, _ = Player.objects.get_or_create(draft=draft, auction_id=auction_id,
                        defaults = { 'name': 'Player' + str(auction_id),
                                     'position': choice(POSITIONS),
                                     'value': randint(1,50) })

        player.timer = timer
        player.save()

    def _handle_status(self, args):
        """ This function processes the auction status message from the Jack server.
        It updates the auction with the current high bidder/manager, bid, and resets
        the auction timer.
        """

        draft_id = args['sessionId']
        auction_id = args['auctionId']
        timer = int(args['timer'])

        draft = Draft.objects.get(id=draft_id)
        player = Player.objects.get(auction_id=auction_id)
        player.timer = timer

        if 'bidderId' in args:
            manager_id = args['bidderId']
            bid = int(args['bid'])
            manager = Manager.objects.get(draft=draft, id=manager_id)
            player.manager = manager
            player.bid = bid

        player.save()

    def _handle_stop(self, args):
        """ This function processes the stop auction message from the Jack server.
        It updates the auction with the winning bidder/manager, top bid, and resets
        the timeer to be null.
        """

        # TODO: It is probably worth considering fusing this message with the status
        # message above because they are so similar.

        draft_id = args['sessionId']
        auction_id = args['auctionId']

        draft = Draft.objects.get(id=draft_id)
        player = Player.objects.get(auction_id=auction_id)
        player.timer = None

        if 'bidderId' in args:
            manager_id = args['bidderId']
            bid = int(args['bid'])
            manager = Manager.objects.get(draft=draft, id=manager_id)
            manager.budget -= bid
            manager.save()
            player.manager = manager
            player.bid = bid

        player.save()

def main(argv):
    #logging.basicConfig(stream=sys.stderr, level=logging.DEBUG)
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('127.0.0.1', 1300))

        # Reset the draft
        draft, _ = Draft.objects.get_or_create(id=1,
                       defaults = { 'maxmanagers': 10,
                                    'quota': 'QB,RB,WR,TE,DEF,K' })

        Manager.objects.filter(draft=draft).delete()
        Player.objects.filter(draft=draft).delete()

        # Destroy any sessions
        sessions = Session.objects.all().delete()

        sender = Sender(sock)
        receiver = Receiver(sock)

        sender.start()
        receiver.start()
        sender.stop()

    except KeyboardInterrupt:
        sender.stop()
        pass


if __name__ == "__main__":
    sys.exit(main(sys.argv))
