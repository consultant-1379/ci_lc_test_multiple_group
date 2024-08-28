import os
import sys
# Script's arguments
def main():
    print ("Number of arguments", len(sys.argv))
    for i in range(len(sys.argv)) :
        print (sys.argv[i])
        
    drop_var  = sys.argv[1] 
    psw_var = sys.argv[2] 
    #user_var = sys.argv[3] 

    user_var1  = os.environ.get('testUser')
    psw_var1  = os.environ['testPwd']

    print  ("drop_var: ", drop_var)
    print  ("psw_var: ", "****")
    #print  ("user_var: ", user_var)

    print  ("psw_var1: ", "****")
    print  ("user_var1: ", "****")

if __name__ == '__main__':
    main()