import sys
import requests
import csv
from os import path

from lib import PreparedSession, ReleaseNote, Deliverable


def letter_conv(col):
    alphabet = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'S', 'T', 'U', 'V', 'X', 'Y', 'Z']
    quot, rem = divmod(col - 1, 20)
    return letter_conv(quot) + alphabet[rem] if col != 0 else ''


def get_rstate_from_version(version):
    # https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/CIOSS/Version+Management
    mayor, minor, incr = tuple(version.split('.'))
    return "R{mayor}{letter_conv(int(minor) + 1)}{int(incr):02d}"


class ChecksumRetriever():
    checksum_cache_file = path.join(path.dirname(__file__), 'checksums.csv')

    def __init__(self):
        self.checksum_cache = {}
        self.load()

    def load(self):
        if path.isfile(self.checksum_cache_file):
            with open(self.checksum_cache_file, newline='') as f:
                for r in csv.reader(f):
                    self.checksum_cache[r[0]] = r[1]
        # print(self.checksum_cache)

    def get_checksum(self, mediaurl):
        if mediaurl not in self.checksum_cache:
            r = requests.get(mediaurl + '.md5')
            if r.status_code != 200:
                print(r.text, file=sys.stderr)
                return
            self.checksum_cache[mediaurl] = r.text
        return self.checksum_cache[mediaurl]

    def save(self):
        with open(self.checksum_cache_file, 'w', newline='') as f:
            writer = csv.writer(f)
            writer.writerows(self.checksum_cache.items())


class CI():
    def __init__(self, productset: str, drop: str, dropversion: str = None):
        self.s = PreparedSession('https://ci-portal.seli.wh.rnd.internal.ericsson.com')
        self.ps = productset
        self.drop = drop
        self.psv = dropversion or self.get_last_good_dropversion()
        print('Using productset version: %s' % self.psv, file=sys.stderr)
        self.csr = ChecksumRetriever()

    def get_aom_rstate(self):
        r = self.s.get('/getAOMRstate/', params={'product': self.ps, 'drop': self.drop})
        r.raise_for_status()
        r = r.text.split()
        return (''.join(r[:-1]), r[-1])

    def get_release_note(self):
        r = self.s.get('/api/getReleaseNote/%s/%s/' % (self.ps, self.psv))
        r.raise_for_status()
        rn = r.json()
        rn['rstate'] = self.get_aom_rstate().split()[-1]
        return ReleaseNote(rn)

    def get_last_good_dropversion(self):
        r = self.s.get('/getLastGoodProductSetVersion/', params={'productSet': self.ps, 'drop': self.drop})
        r.raise_for_status()
        return r.text

    def get_content(self):
        r = self.s.get('/getProductSetVersionContents/', params={
            'drop': self.drop,
            'productSet': self.ps,
            'version': self.psv
        })
        psvc = r.json()[0]
        for c in psvc['contents']:
            filename = c['hubUrl'].split('/')[-1]
            yield Deliverable({
                'functionalDesignation': c['artifactName'],
                'productNumber': c['artifactNumber'],
                'rstate': get_rstate_from_version(c['version']),
                'checksum': self.csr.get_checksum(c['hubUrl']),
                'filename': filename
            })
        self.csr.save()
