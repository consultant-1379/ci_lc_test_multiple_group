#!/usr/bin/env python3

import sys
import getpass
import json

from lib.confluence_releasenote import ConfluenceReleaseNote
from lib.ci import CI
from lib.swgw_py3 import SWGW


if len(sys.argv) < 2:
    print('Usage: %s DROP' % sys.argv[0])
    raise SystemExit(1)

drop = sys.argv[1]
ps = 'ENM'

print('Username: ', file=sys.stderr, flush=True, end='')
user = input('')
print('Password: ', file=sys.stderr, flush=True, end='')
pw = getpass.getpass('')
# print('', file=sys.stderr, flush=True)

ci = CI(ps, drop)
crn = ConfluenceReleaseNote(drop, (user, pw))
#                                                                                                 !!!
#                               PLEASE REPLACE THESE CREDENTIALS ASAP                             !!!
#                                                                                                 !!!
swgw = SWGW('940821', '51ac4171-d97d-4ac5-be33-317219fcdcaf', '58df9190-1ba0-47d6-800d-1337476f8b6d')


for t in crn.softwareGatewayTickets:
    swgw.load_ticket_content(t['ticket'])

r = ci.get_content()
r = crn.patch_deliverables(r)
r = swgw.patch_deliverables(r)

pn, rstate = ci.get_aom_rstate()
rn = {
    'deliverables': list(map(lambda d: d.dump(), r)),
    'softwareGatewayTickets': crn.softwareGatewayTickets,
    'productNumber': pn,
    'drop': drop,
    'productSet': ps,
    'rstate': rstate
}
print(json.dumps(rn, indent=2))
