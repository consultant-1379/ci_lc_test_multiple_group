#!/usr/bin/env python3

from lxml import etree
import re
import sys

from lib import PreparedSession


class ConfluenceReleaseNote():
    def __init__(self, drop, auth):
        s = PreparedSession('https://confluence-oss.seli.wh.rnd.internal.ericsson.com')
        s.auth = auth

        r = s.get('/rest/api/content', params={'title': "ENM {drop} Release Note", 'spaceKey': 'ENMPIFOA'})
        if r.status_code != 200:
            print(r.text, file=sys.stderr)
            raise SystemExit(2)
        r = r.json()
        if not r['results']:
            return
        page_id = r['results'][0]['id']

        r = s.get('/rest/api/content/' + page_id, params={'expand': 'body.storage'})
        r = etree.fromstring(r.json()['body']['storage']['value'], parser=etree.HTMLParser())
        tables = r.xpath('//table')

        self.deliverables = {}

        for tr in tables[1].xpath('.//tr'):
            tds = tr.xpath('./td')
            if tds:
                pn = re.sub(r'\s+', '', ''.join(tds[1].itertext()))
                rstate = re.sub(r'\s+', '', ''.join(tds[2].itertext()))
                if pn:
                    real_pn = pn
                    pn = pn.split('/', 1)[0]
                    self.deliverables[pn] = (real_pn, rstate)

        self.softwareGatewayTickets = []

        for tr in tables[3].xpath('.//tbody/tr'):
            tds = tr.xpath('./td')
            tnum = ''.join(tds[0].itertext())
            tname = ''.join(tds[1].itertext())
            self.softwareGatewayTickets.append({
                'ticket': tnum,
                'name': tname
            })

    def patch_deliverables(self, ds):
        for d in ds:
            rs = re.match(r'(R\d+[A-Z]+)\d+', d.rstate).group(1)
            if d.productNumber in self.deliverables:
                pn_prim, rs_prim = self.deliverables[d.productNumber]
                d.productNumber = pn_prim
                if rs != rs_prim:
                    d.internalRstate = rs
                d.rstate = rs_prim
                yield d
            else:
                print('No Such deliverable: %s' % d.productNumber, file=sys.stderr)
