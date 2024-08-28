#!/usr/bin/env python3

#from lib.swgw import SWGW
from lib.swgwswgw_py3 import SWGW




def main():
    
#                                                                                                 !!!
#                               PLEASE REPLACE THESE CREDENTIALS ASAP                             !!!
#                                                                                                 !!!
    swgw = SWGW('940821', '51ac4171-d97d-4ac5-be33-317219fcdcaf', '58df9190-1ba0-47d6-800d-1337476f8b6d')
    swgw.load_ticket_content('T-122292')




if __name__ == '__main__':
    main()





