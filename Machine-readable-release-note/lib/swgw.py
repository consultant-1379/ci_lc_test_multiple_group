#!/usr/bin/python

import requests
from lxml import etree
import sys
from os import path, mkdir
from lib import PreparedSession


class SWGW():
    def __init__(self, euft, username, password):
        self.s = PreparedSession('https://apiswgw.ericsson.net')
        self._credentials = requests.auth.HTTPBasicAuth(username, password)
        self.euft = euft
        self._create_ticket_cache()
        self.swgwMap = {}

    def auth(self):
        print('Authenticating')
        r = self.s.post(
            '/auth/oauth/v2/token',
            params={'grant_type': 'client_credentials', 'scope': 'downloadAPI'},
            headers={'Content-Type': 'application/x-www-form-urlencoded'},
            auth=self._credentials
        )
        if r.status_code != 200:
            print(r.text) #exceptions are written to sys.stderr
            raise SystemExit(1)
        self.token = r.json()['access_token']
        print('Successfully authenticated')

    def _create_ticket_cache(self):
        self._ticket_cache = path.join(path.dirname(__file__), 'tickets')
        if path.exists(self._ticket_cache) and not path.isdir(self._ticket_cache):
            print('ERROR: %s is not a directory' % self._ticket_cache)
            raise SystemExit(1)
        if not path.exists(self._ticket_cache):
            print('Creating directory: %s' % self._ticket_cache)
            mkdir(self._ticket_cache)

    def get_ticket_xml(self, ticket):
        ticket_file = path.join(self._ticket_cache, ticket + '.xml')
              
        if not path.exists(ticket_file):
            print('Ticket has not been found in the local cache')
            if not hasattr(self, 'token'):
                self.auth()
            print('Downloading')
            r = self.s.get(
                '/EI180804',
                params={'search': 'ticketxml', 'EUFT': self.euft, 'Ticket': ticket},
                headers={'Authorization': 'Bearer ' + self.token},
                stream=True
            )
            if r.status_code != 200:
                print(r.text)
                return
            with open(ticket_file, 'wb') as f:
                for chunk in r.iter_content(chunk_size=8192):
                    f.write(chunk)
            r.close()
            print('\nSuccessfully downloaded')
        
        print "*********************"
        print "Print XML content: \n"
        with open(ticket_file, 'r') as file:
            for line in file.readlines():
                print line
        print "*********************"  
        return etree.parse(ticket_file)

    def load_ticket_content(self, ticket):
        print "Ticket: \n", ticket
        root = self.get_ticket_xml(ticket)
        #print "*********************"
        #print "Print Ticket content: \n", root.text()
        #print "*********************"
        
        if not root:
            return
        # name = root.xpath('/Ticket/Header/text()')[0]
        re = {}
        for b in root.xpath('/Ticket/Boxes/Box'):
            pn = b.xpath('./ProductNumber/text()')[0]
            rstate = b.xpath('./RState/text()')[0]
            filename = 'NOT FOUND'  #  filename in json file
            for d in b.xpath('./Documents/Document'):
                df = d.xpath('./DataFormat/text()')[0]
                if df in ['ISO9660', 'ISO', 'TAR', 'TAR_GZIPV1', 'GZIPV1']:
                    # dn = d.xpath('./DocumentNumber/text()')[0]
                    filename = d.xpath('./Filename/text()')[0]
                    break
            re[(pn, rstate)] = filename
        print('Successfully parsed')
        self.swgwMap.update(re)
        
        print "*********************"
        print "self.swgwMap.update(re): \n", self.swgwMap
        print "*********************"
        self.writeSWGWinfoToFile()

    def writeSWGWinfoToFile(self):
        with open('swgwInfo.txt', 'w') as file:
            for line in self.swgwMap:
                #file.write(str(line) +"\n")
                #file.write(line, '->', self.swgwMap[line])                
    

    def patch_deliverables(self, ds):
        for d in ds:
            k = (d.productNumber, d.rstate)
            if k not in self.swgwMap:
                print('%s %s was not delivered to SWGW' % k)
                continue
            filename = self.swgwMap[k]
            if d.filename != filename:
                print('Filename mismatch for %s %s\nFixing' % k)
                d.filename = filename
            yield d
