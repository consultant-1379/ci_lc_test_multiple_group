from requests import Session
from urllib.parse import urljoin


class PreparedSession(Session):
    def __init__(self, prefix_url=None, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.prefix_url = prefix_url

    def request(self, method, url, *args, **kwargs):
        url = urljoin(self.prefix_url, url)
        return super().request(method, url, *args, **kwargs)


class Deliverable():
    def __init__(self, obj):
        self.__dict__['_obj'] = obj

    def __getattr__(self, attr):
        return self._obj[attr]

    def __setattr__(self, attr, v):
        self.__dict__['_obj'][attr] = v

    def __repr__(self):
        return '<Deliverable %s-%s>' % (self.productNumber, self.rstate)

    def dump(self):
        return self._obj


class ReleaseNote():
    def __init__(self, obj):
        self.deliverables = list(map(lambda d: Deliverable(d), obj['deliverables']))
        self.softwareGatewayTickets = obj['softwareGatewayTickets']
        self.productNumber = obj['productNumber']
        self.drop = obj['drop']
        self.productSet = obj['productSet']
        self.rstate = obj['rstate']

    def __repr__(self):
        return "<ReleaseNote [ {self.productSet} | {self.drop} ] {self.productNumber}-{self.rstate} {self.deliverables} {self.softwareGatewayTickets}>"

    def dump(self):
        return {
            'deliverables': list(map(lambda d: d.dump(), self.deliverables)),
            'softwareGatewayTickets': self.softwareGatewayTickets,
            'productNumber': self.productNumber,
            'drop': self.drop,
            'productSet': self.productSet,
            'rstate': self.rstate
        }
