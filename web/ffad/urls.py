# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

from django.conf.urls import patterns, url

from ffad import views

urlpatterns = patterns('',
    url(r'^$', views.index, name='index'),
    url(r'^(?P<draft_id>\d+)/$', views.draft),
    url(r'^(?P<draft_id>\d+)/register$', views.register),
    url(r'^(?P<draft_id>\d+)/get_manager_updates$', views.get_manager_updates),
    url(r'^(?P<draft_id>\d+)/get_player_updates$', views.get_player_updates),
    url(r'^(?P<draft_id>\d+)/get_team$', views.get_team),
    url(r'^(?P<draft_id>\d+)/place_bid$', views.place_bid)
)
