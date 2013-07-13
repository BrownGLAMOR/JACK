# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Lesser General Public License as published by
# the Free Software Foundation, either version 3 of the License, or (at your
# option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

from Queue import Queue
from random import choice, randint
from threading import Thread
import socket
import sys
import time
import asyncore

# The below includes are necessary in order to use our models. This is
# effectively what the django shell does to setup its environment. I believe
# that there is a simplier way to do this in newer versions of django.

from django.core.management import setup_environ

# Get the jackweb settings

sys.path.append('..')
from jackweb import settings
setup_environ(settings)

from django.db.utils import IntegrityError

# Finally import our ffad models!

from ffad.models import POSITIONS, Draft, Manager, Player





def _process_bidder(args):
    """ This function processes the bidder notification message from the Jack
    server. It signals the participation of a new biider/manager in the
    specified draft. To do this we simply add a new manager row to the database.
    """

    draft_id = args['draft']
    name = args['name']
    budget = int(args['budget'])

    draft = Draft.objects.get(id=draft_id)
    manager = Manager(draft=draft, name=name, budget=budget)
    manager.save()

def _process_auction(args):
    """ This function processes the auction notification message from the Jack
    server. It alerts the participants to a new auction in the schedule.
    """

    draft_id = args['draft']
    order = int(args['order'])
    name = args['player']
    position = args['position'] # Validated on save
    value = int(args['value'])

    draft = Draft.objects.get(id=draft_id)
    player = Player(draft=draft, order=order, name=name, position=position, value=value)
    player.save()

def _process_start(args):
    """ This function processes the start auction message from the Jack server.
    In terms of the Fantasy Football Auction Draft, auctions are equivalent to
    players. An auction is considered to be in progress when its timer is a
    positive integer.
    """

    draft_id = args['draft']
    order = args['order']
    timer = int(args['timer'])

    draft = Draft.objects.get(id=draft_id)
    player = Player.objects.get(draft=draft_id, order=order)
    player.timer = timer
    player.save()

def _process_status(args):
    """ This function processes the auction status message from the Jack server.
    It updates the auction with the current high bidder/manager, bid, and resets
    the auction timer.
    """

    draft_id = args['draft']
    order = args['auction']
    manager_id = args['bidder']
    bid = int(args['bid'])
    timer = int(args['timer'])

    draft = Draft.objects.get(id=draft_id)
    player = Player.objects.get(draft=draft, order=order)
    manager = Manager.objects.get(draft=draft, id=manager_id)

    player.manager = manager
    player.bid = bid
    player.timer = timer
    player.save()

def _process_stop(args):
    """ This function processes the stop auction message from the Jack server.
    It updates the auction with the winning bidder/manager, top bid, and resets
    the timeer to be null.
    """

    # TODO: It is probably worth considering fusing this message with the status
    # message above because they are so similar.

    draft_id = args['draft']
    order = args['auction']
    manager_id = args['bidder']
    bid = int(args['bid'])

    draft = Draft.objects.get(id=draft_id)
    player = Player.objects.get(id=order)
    manager = Manager.objects.get(draft=draft, id=manager_id)

    player.manager = manager
    player.bid = bid
    player.timer = None
    player.save()

# These functions process commands based on the command code

handlers = {
    'bidder': _process_bidder,
    'auction': _process_auction,
    'start': _process_start,
    'status': _process_status,
    'stop': _process_stop
}

outgoing = Queue()
class JackClient(asyncore.dispatcher):

    def __init__(self, host, port):
        asyncore.dispatcher.__init__(self)
        self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
        self.connect((host, port))

    def handle_connect(self):
        pass

    def handle_close(self):
        self.close()

    def handle_read(self):
        message = self.recv(1024)
        #command, keyvals = message.split(None, 1)
        #args = dict(keyval.split('=',1) for keyval in keyvals.split())
        #handlers[command](args)

    def writable(self):
        return not outgoing.empty()

    def handle_write(self):
        message = outgoing.get()
        sent = self.send(message)

def start():
    c = JackClient('127.0.0.1', 1300)
    t = Thread(target=asyncore.loop, kwargs={'timeout': 0.5})
    t.start()

def register_manager(draft_id, name):
    message = 'bidder draft=' + str(draft_id) + ' name=' + name;
    outgoing.put(message)


def send_bid(draft_id, order, bidder_name, bid):
    message = 'bid draft=' + str(draft_id) + \
              ' order=' + str(order) + \
              ' bidder=' + bidder_name + \
              ' bid=' + str(bid)
    outgoing.put(message)

def jack_cmdline():
    """ Initiates a very simple command line interface where the user can
    simulate commands that would be sent from the jack auction server.
    """
    while True:
        try:
            message = raw_input("jack >>> ")
            command, keyvals = message.split(None, 1)

            # Convert keyvals from 'key1=value1 ... keyN=valueN' into a dict
            args = dict(keyval.split('=',1) for keyval in keyvals.split())
            handlers[command](args)

        except Exception as e:
            print 'Invalid command: ', e

def _test_add_draft(name):
    """ This function initializes a new draft with the given identifier, number
    of managers, and quota. Only for testing purposes.
    """

    draft = Draft(id=name, maxmanagers=2, quota='QB,RB,RB,WR,WR,TE,DEF,K')
    draft.save()
    print 'Initialized draft', draft.id

    _test_add_players(name, 20)
    print 'Added 20 players'

def _test_add_managers(draft_id, num_managers):
    """ This function adds the specifed number of managers to the draft. Only
    for testing purposes.
    """

    draft = Draft.objects.get(id=draft_id)
    for i in range(num_managers):
        try:
            manager = Manager(draft=draft,
                              name=' '.join(('Manager', str(i + 1))),
                              budget=200)
            manager.save()
        except IntegrityError as e:
            print 'Skipping duplicate entry'
            pass

def _test_add_players(draft_id, num_players):
    """ This function add the specified number of players to the draft. Only for
    testing purposes.
    """

    draft = Draft.objects.get(id=draft_id)
    for i in range(num_players):
        try:
            player = Player(draft=draft,
                            order=i+1,
                            name=' '.join(('Player', str(i + 1))),
                            position=choice(POSITIONS),
                            value=randint(1,50))
            player.save()
        except IntegrityError as e:
            print 'Skipping duplicate entry'
            pass

