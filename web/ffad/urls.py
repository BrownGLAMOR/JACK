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
