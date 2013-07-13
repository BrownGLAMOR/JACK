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

from time import sleep
from sys import stderr
from random import randint
from datetime import datetime, date
from dateutil import parser

from django.http import HttpResponse
from django.shortcuts import get_object_or_404, get_list_or_404
from django.shortcuts import render
from django.views.decorators.http import require_GET, require_POST

from ffad.models import Draft, Manager, Player, Bid
from ffad.myjson import dumps

min_time_str = datetime(1900, 1, 1).isoformat()

def index(request):
    latest_draft_list = Draft.objects.all()
    context = {'latest_draft_list': latest_draft_list}
    return render(request, 'ffad/index.html', context)

def draft(request, draft_id):
    registered = 'manager' in request.session
    draft = get_object_or_404(Draft, id=draft_id)
    context = {'draft': draft, 'managers': [], 'players': [], 'registered': registered}
    return render(request, 'ffad/draft.html', context)

@require_GET
def register(request, draft_id):
    manager_name = request.GET['manager']
    request.session['manager'] = manager_name
    draft = Draft.objects.get(id=draft_id)
    Manager.objects.get_or_create(draft=draft, name=manager_name)
    return HttpResponse()


@require_GET
def get_manager_updates(request, draft_id):
    """ This function is triggered by an HTTP GET request. It returns all of the
    managers in the database that have been modified since the time specified in
    the request. The data is formatted in JSON along with a timestamp of when
    the request was made.
    """

    now = datetime.now()
    since_str = request.GET.get('since', min_time_str)
    since = parser.parse(since_str)

    managers = Manager.objects.filter(draft=draft_id, verified=True, modified__gt=since)

    response = {'time': now.isoformat(), 'managers': managers}
    return HttpResponse(dumps(response), content_type="application/json")

@require_GET
def get_player_updates(request, draft_id):
    """ This function is triggered by an HTTP GET request. It returns all of the
    players in the database that have been modified since the time specified in
    the request. The data is formatted in JSON along with a timestamp
    of when the request was made.
    """

    now = datetime.now()
    since_str = request.GET.get('since', min_time_str)
    since = parser.parse(since_str)

    managers = Manager.objects.filter(draft=draft_id, verified=True)
    players = Player.objects.filter(draft=draft_id, modified__gt=since)

    response = {'time': now.isoformat(), 'managers': managers, 'players': players}
    return HttpResponse(dumps(response), content_type="application/json")

@require_GET
def get_team(request, draft_id):
    """ This function is triggered by an HTTP GET request. It returns all of the
    players in this draft that have been drafted by the manager specified in the
    request. The data is formatted in JSON along with that managers value.
    """

    manager_name = request.session['manager']

    draft = Draft.objects.get(id=draft_id)
    manager = Manager.objects.get(draft=draft, name=manager_name)
    players = Player.objects.filter(draft=draft, manager=manager, timer=None)

    # Now we want to sort the list of players based on the quotas specified in
    # the draft and their values.

    team = []
    players = players.order_by('-value')
    for pos in draft.quota:
        slot = [pos, '', '']
        p = players.filter(position=pos)
        if p.exists():
            slot[1] = p[0].name
            slot[2] = p[0].value
            players = players.exclude(id=p[0].id)
        team.append(slot)

    # Add additional players that do not contribute to the teams overall value

    for p in players:
        team.append([p.position, p.name, p.value])

    response = {'team': team}
    return HttpResponse(dumps(response), content_type="application/json")

@require_POST
def place_bid(request, draft_id):
    manager_name = request.session['manager']

    draft = Draft.objects.get(id=draft_id)
    manager = Manager.objects.get(draft=draft, name=manager_name)
    player = Player.objects.get(draft=draft, timer__gt=0)

    bid = Bid(manager=manager, player=player, value=request.POST['bid'])
    bid.save()

    return HttpResponse()
