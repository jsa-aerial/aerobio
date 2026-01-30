import os
import sys
import pwd
import getpass
import getopt, sys

import warnings
with warnings.catch_warnings():
    warnings.simplefilter("ignore", category=DeprecationWarning)
    import pkg_resources

import time
import trio
import client as cli
from client import close, error, bpwait, bpresume, sent, stop, rmv

udb = {}

def get_udb (keys):
    return cli.get_db(udb, keys)

def update_udb (keys, val):
    cli.update_db(udb, keys, val)


dsdict = lambda dict, *keys: list((dict[key] for key in keys))


def appmsg (ws, data):
    op, payload = dsdict(data, cli.op, cli.payload)
    if op == cli.keyword('validate'):
        print("")
        print(payload)
    elif op == cli.keyword("error"):
        print("")
        if ("Error" == payload[0:5]):
            print(payload)
        else:
            print("Error :", payload)
    elif op == cli.keyword("launch"):
        print("")
        print("Job launch: ", payload)
    elif op == cli.keyword('status'):
        print(payload)
    elif op == cli.keyword('register'):
        update_udb(['register'], payload)
    else:
        print("UOP:", op, "\nUPAYLOAD", payload)

def dispatcher (ch, op, payload):
    #print("DISPATCH:", op, payload)

    if op == cli.msg or op == 'msg':
        ws, data = dsdict(payload, 'ws', 'data')
        #print('CLIENT :msg/payload = ', payload)
        update_udb([ws, 'lastrcv'], data)
        update_udb([ws, 'rcvcnt'], get_udb([ws, 'rcvcnt'])+1)
        appmsg(ws, data)
    elif op == cli.sent:
        ws, msg = dsdict(payload, 'ws', cli.msg)
        #print("CLIENT, Sent msg ", msg)
        update_udb([ws, 'lastsnt'], msg)
        update_udb([ws, 'sntcnt'], get_udb([ws, 'sntcnt'])+1)
    elif op == cli.open:
        ws = payload
        #print("CLIENT :open/ws = ", ws)
        update_udb([ws], {'chan': ch, 'rcvcnt': 0, 'sntcnt': 0, 'errcnt': 0})
        update_udb(['com'], [ch, ws])
    elif op == close:
        ws, code, reason = dsdict(payload, 'ws', 'code', 'reason')
        print("CLIENT RMTclose/payload = ", payload)
    elif op == error:
        ws, err = dsdict(payload, 'ws', 'err')
        print("CLIENT :error/payload = ", payload)
        update_udb([ws, 'errcnt'], get_udb([ws, 'errcnt'])+1)
    elif op == bpwait:
        ws, msg, encode = dsdict(payload, 'ws', cli.msg, 'encode')
        print("CLIENT, Waiting to send msg ", msg)
        v = udb["bpwait"] if "bpwait" in udb else []
        v.append([ws, msg, encode])
        update_udb(["bpwait"], v)
        time.sleep(0.1)
    elif op == bpresume:
        print("CLIENT, BP Resume ", payload)
        update_udb(["resume"], payload)
    elif op == stop:
        ws, cause = dsdict(payload, 'ws', 'cause')
        print("CLIENT, Stopping reads... Cause ", cause)
        update_udb([ws], rmv)
    else:
        print("CLIENT :WTF/op = ", op, " payload = ", payload)



# msg attributes as keywords
#
kwcmd = cli.keyword("cmd")
kwuser = cli.keyword("user")
kwphase = cli.keyword("phase")
kwaction = cli.keyword("action")
kwargs = cli.keyword("args")
kwmodifier = cli.keyword("modifier")
kweid = cli.keyword("eid")
kwcompfile = cli.keyword("compfile")

def getarg (arglist):
    arg = arglist[0]
    arglist = arglist[1:]
    return (arg, arglist)

def args2map ():
    user = os.environ['USER']
    argmap = {kwuser: user}
    fullArgs = sys.argv
    arglist = fullArgs[1:]

    if (len(arglist) < 1):
        print(pkg_resources.resource_string(
            __name__, "resources/usage.txt").decode("utf-8").strip())
        sys.exit()

    (cmd,arglist) = getarg(arglist)
    argmap[kwcmd] = cli.keyword(cmd)

    if cmd == 'run':
        (phase,arglist) = getarg(arglist)
        argmap[kwphase] = phase
        (x, arglist) = getarg(arglist)
        if (x == 'replicates') or (x == 'combined'):
            argmap[kwmodifier] = x
        else:
            argmap[kweid] = x
        if not kweid in argmap:
            (eid, arglist) = getarg(arglist)
            argmap[kweid] = eid
        if not kwmodifier in argmap:
            argmap[kwmodifier] = 'replicates'
    elif (cmd == 'compare') or (cmd == 'xcompare') or (cmd == 'aggregate'):
        (compfile, arglist) = getarg(arglist)
        argmap[kwcompfile] = compfile
        (eid, arglist) = getarg(arglist)
        argmap[kweid] = eid
    elif (cmd == 'status'):
        (action,arglist) = getarg(arglist)
        argmap[kwaction] = action
        (eid,arglist) = getarg(arglist)
        argmap[kweid] = eid
    elif (cmd == 'check'):
        (eid,arglist) = getarg(arglist)
        argmap[kweid] = eid
    elif (cmd == 'reset'):
        (action,arglist) = getarg(arglist)
        argmap[kwaction] = action
        (eid,arglist) = getarg(arglist)
        argmap[kweid] = eid
    else:
        argmap[kwargs] = arglist
        argmap[kweid] = "fake-eid"
    return argmap


def get_port ():
    users = map(lambda x: x[0], pwd.getpwall())
    curuser = getpass.getuser()
    hmdir = os.path.expanduser("~{0}".format(curuser))

    if os.path.isdir(os.path.join(hmdir, ".aerobio")):
        portfile = os.path.join(hmdir, ".aerobio/.ports")
    elif "aerobio" in users:
        hmdir = os.path.expanduser("~aerobio")
        portfile = os.path.join(hmdir, ".aerobio/.ports")
    else:
        print("Cannot find an Aerbio home/install directory")
        sys.exit()

    ## print("Port file:", portfile)
    a = None
    if os.path.isfile(portfile):
        with open(portfile) as ports:
            a = eval(ports.read())
        if a != None:
            return a['http']
        else:
            print("Bad port file {0}".format(portfile))
            sys.exit()
    else:
        print("Cannot find port file {0}".format(portfile))
        sys.exit()



async def bpretry ():
    return "bpwait" in udb and udb["bpwait"]

async def resume ():
    while "resume" not in udb:
        await trio.sleep(0.1)
    return True

async def command (info):
    while not 'com' in udb:
        time.sleep(0.1)
    ws = info["ws"]
    argmap = info["appinfo"]["argmap"]
    await cli.send_msg(
        ws, {cli.op: argmap[kwcmd], cli.keyword('data'): argmap})


def main():
    ## print("Hello from Aerobio Python")
    argmap = args2map()
    #print("ArgMap:", argmap)
    url = 'ws://localhost:' + get_port() + '/ws'
    trio.run(cli.open_connection,
             url, dispatcher, command, {"argmap": argmap})


if __name__ == "__main__":
    main()




# from client import get_db, cli_db, msgsnt, msgrcv
# ex.startit('ws://localhost:8765/ws')
# ch,ws = ex.udb['com']
# for i in range(95): cli.send_msg(ws, {'op': "msg", 'payload': 'testing'})
# [ex.get_udb([ws, 'sntcnt']), ex.get_udb([ws, 'rcvcnt'])]
# [get_db(cli_db, [ws, msgsnt]), get_db(cli_db, [ws, msgrcv])]

## cd aerobio/
## zip -r ../aerobio.zip *
## cd ..
## echo "#\!/usr/bin/env python3" | cat - aerobio.zip > aerobiov2
