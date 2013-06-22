from datetime import datetime

from django.db import models

POSITIONS = ('QB','RB', 'WR', 'TE', 'DEF', 'K')

class SeparatedValuesField(models.TextField):
    __metaclass__ = models.SubfieldBase

    def __init__(self, *args, **kwargs):
        self.token = kwargs.pop('token', ',')
        super(SeparatedValuesField, self).__init__(*args, **kwargs)

    def to_python(self, value):
        if not value:
            return
        if isinstance(value, list):
            return value
        return value.split(self.token)

    def get_db_prep_value(self, value, connection, prepared=False):
        if not value:
            return
        assert(isinstance(value, list) or isinstance(value, tuple))
        return self.token.join([unicode(s) for s in value])

    def value_to_string(self, obj):
        value = self._get_val_from_obj(obj)

class Draft(models.Model):
    maxmanagers = models.IntegerField()
    quota = SeparatedValuesField()
    modified = models.DateTimeField()

    def save(self, *args, **kwargs):
        self.modified = datetime.now()
        super(Draft, self).save(*args, **kwargs)

class Manager(models.Model):
    draft = models.ForeignKey(Draft)
    name = models.CharField(max_length=32)
    budget = models.IntegerField(default=0)
    value = models.IntegerField(default=0)
    verified = models.BooleanField(default=False)
    modified = models.DateTimeField()

    class Meta:
        unique_together = ('draft', 'name')

    def save(self, *args, **kwargs):
        self.modified = datetime.now()
        super(Manager, self).save(*args, **kwargs)

class Player(models.Model):
    draft = models.ForeignKey(Draft)
    auction_id = models.IntegerField()
    order = models.IntegerField()
    name = models.CharField(max_length=32)
    position = models.CharField(max_length=3, choices=zip(POSITIONS, POSITIONS))
    value = models.IntegerField()
    manager = models.ForeignKey(Manager, null=True, blank=True, default=None)
    bid = models.IntegerField(null=True, blank=True, default=None)
    timer = models.IntegerField(null=True, blank=True, default=None)
    modified = models.DateTimeField()

    class Meta:
        unique_together = ('draft', 'order')

    def save(self, *args, **kwargs):
        self.modified = datetime.now()
        super(Player, self).save(*args, **kwargs)

class Bid(models.Model):
    player = models.ForeignKey(Player, related_name='+')
    manager = models.ForeignKey(Manager)
    value = models.IntegerField()
    processed = models.BooleanField(default=False)
