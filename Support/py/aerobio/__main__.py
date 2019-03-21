import time
import pkg_resources
import asyncio
import gochans as gc
import client as cli
from client import close, error, bpwait, bpresume, sent, stop, rmv

udb = {}

def get_udb (keys):
    return cli.get_db(udb, keys)

def update_udb (keys, val):
    cli.update_db(udb, keys, val)


kwdone = cli.keyword('done')

dsdict = lambda dict, *keys: list((dict[key] for key in keys))



def usermsg (op, payload):
    print("UOP:", op, "\nUPAYLOAD", payload)


def dispatcher (ch, op, payload):
    ## print("DISPATCH:", op, payload)

    if op == cli.msg or op == 'msg':
        ws, appmsg = dsdict(payload, 'ws', 'data')
        mop = cli.get_msg_op(appmsg)
        mpload = cli.get_msg_payload(appmsg)
        if mop == kwdone or mop == 'done':
          ## print("close_connection and exit ...")
          closech = get_udb(['close', 'chan'])
          gc.go(closech.send, 'stop')
        else:
          update_udb([ws, 'lastrcv'], appmsg)
          update_udb([ws, 'rcvcnt'], get_udb([ws, 'rcvcnt'])+1)
          usermsg(mop, mpload)
    elif op == cli.sent:
        ws, msg = dsdict(payload, 'ws', cli.msg)
        print("CLIENT, Sent msg ", msg)
        update_udb([ws, 'lastsnt'], msg)
        update_udb([ws, 'sntcnt'], get_udb([ws, 'sntcnt'])+1)
    elif op == cli.open:
        ws = payload
        ##print("CLIENT :open/ws = ", ws)
        update_udb([ws], {'chan': ch, 'rcvcnt': 0, 'sntcnt': 0, 'errcnt': 0})
        update_udb(['com'], [ch, ws])
    elif op == close:
        ws, code, reason = dsdict(payload, 'ws', 'code', 'reason')
        print("CLIENT RMTclose/payload = ", payload)
        go(ch.send, {cli.op: stop,
                     cli.payload: {'ws': ws, 'cause': 'rmtclose'}})
    elif op == error:
        ws, err = dsdict(payload, 'ws', 'err')
        print("CLIENT :error/payload = ", payload)
        update_udb([ws, 'errcnt'], get_udb([ws, 'errcnt'])+1)
    elif op == bpwait:
        ws, msg, encode = dsdict(payload, 'ws', cli.msg, 'encode')
        print("CLIENT, Waiting to send msg ", msg)
        time.sleep(2)
        print("CLIENT, Trying resend ...")
        cli.send_msg(ws, msg, encode=encode)
    elif op == bpresume:
        print("CLIENT, BP Resume ", payload)
    elif op == stop:
        ws, cause = dsdict(payload, 'ws', 'cause')
        print("CLIENT, Stopping reads... Cause ", cause)
        cli.close_connection(ws)
        update_udb([ws], rmv)
    else:
        print("CLIENT :WTF/op = ", op, " payload = ", payload)


# 'ws://localhost:8765/ws'
def startit (url):
    close_chan = gc.Chan(size=2)
    update_udb(['close', 'chan'], close_chan)
    ch = cli.open_connection(url)
    cli.gorun(ch, dispatcher)


@asyncio.coroutine
def waitclose ():
  closech = get_udb(['close', 'chan'])
  x = yield from closech.recv()
  return x

import threading
import atexit
cloop = asyncio.get_event_loop()
atexit.register(cloop.close)
threading.Thread(name='closeloop-thread', target=cloop.run_forever).start()


def main():
  ## print("Hello from Aerobio Python")
  ## print(pkg_resources.resource_string(
    ##__name__, "resources/usage.txt").decode("utf-8").strip())
  ## print("connecting to 7070")
  startit('ws://localhost:7070/ws')
  ## closech = get_udb(['close', 'chan'])
  print("go(closech.recv)")
  ## time.sleep(.3)
  ## f = gc.go(closech.recv)
  ## x = f.result()
  f = asyncio.run_coroutine_threadsafe(waitclose(), cloop)
  x = f.result()
  print("closech result: ", x)
  ch, ws = get_udb(['com'])
  cli.close_connection(ws)


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
## echo "#\!/usr/bin/env python37" | cat - aerobio.zip > aerobiov2
