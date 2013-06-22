from django.core import serializers
from django.db.models.query import QuerySet
from django.utils import simplejson
from django.utils.simplejson import JSONEncoder
from django.utils.functional import curry

class DjangoJSONEncoder(JSONEncoder):
    def default(self, obj):
        if isinstance(obj, QuerySet):
            return simplejson.loads(serializers.serialize('json', obj))
        return JSONEncoder.default(self,obj)

dumps = curry(simplejson.dumps, cls=DjangoJSONEncoder)

