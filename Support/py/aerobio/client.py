import trio
import trio_websocket as tws
from trio_websocket import open_websocket_url, connect_websocket_url

import msgpack
import json

import channels
from channels import chan, put, take


class keyword:
    val = None
    def __init__(self, val):
        self.val = val
    def __eq__(self, x):
        return type(x) is keyword and self.val == x.val
    def __hash__(self):
        return hash(self.val)
    def __str__(self):
        return ":" + self.val
    def __repr__(self):
        return ":" + self.val

def default(obj):
    if type(obj) is keyword:
        pks = msgpack.packb(obj.val, default=default, use_bin_type=True)
        return msgpack.ExtType(3, pks)
    raise TypeError("Non packable type: %r" % (obj,))

def ext_hook(code, data):
    #print("CODE: ", code, "DATA: ", data, " TYPE: ", type(data))
    if code == 3:
        x = keyword(msgpack.unpackb(data, raw=False))
        return x
    return msgpack.ExtType(code, data)


## Envelope keys
op = keyword("op")
set = keyword("set")
reset = keyword("reset")
payload = keyword("payload")
msgrcv = keyword("msgrcv")
msgsnt = keyword("msgsnt")
bpsize = keyword("bpsize")
## operators
open = keyword("open")
close = keyword("close")
msg = keyword("msg")
bpwait = keyword("bpwait")
bpresume = keyword("bpresume")
sent = keyword("sent")
error = keyword("error")
stop = keyword("stop")

rmv = keyword("rmv")
cli_db = {}

def get_db(x,keys):
    val = x
    for key in keys:
        val = val[key]
    return val

def update_db (x, keys, val):
    l = len(keys)
    finkey = keys[l-1]
    dbval = x
    for key in keys[0:l-1]:
        if key not in dbval:
            dbval[key] = {}
        dbval = dbval[key]
    if val == rmv:
        del dbval[finkey]
    else:
        dbval[finkey] = val
    return x



def get_msg_op (msg):
    if op in msg:
        return msg[op]
    elif 'op' in msg:
        return msg['op']
    else:
        return 'no_op'

def get_msg_payload (msg):
    if payload in msg:
        return msg[payload]
    elif 'payload' in msg:
        return msg['payload']
    else:
        return 'no_payload'

async def goloop (ch, dispatchfn):
    while True:
        msg = await take(ch)
        mop = get_msg_op(msg)
        mpload = get_msg_payload(msg)
        if mop == stop or mop == "stop":
            break
        elif mop == 'no_op':
            print("WARNING Recv: bad msg envelope no 'op' field: ", msg)
        elif mpload == 'no_payload':
            print("WARNING Recv: bad msg envelope no 'payload' field: ", msg)
        else:
            dispatchfn(ch, mop, mpload)
    #print("GOLOOP exit")




async def send (ws, enc, msg):
    #print("SEND MSG: ", msg)
    if enc == "binary":
        encmsg = msgpack.packb(msg, default=default, use_bin_type=True)
    else:
        encmsg = json.dumps(msg)
    await ws.send_message(encmsg)

async def send_msg(ws, msg, encode="binary"):
    kwmsg = keyword("msg")
    msntcnt = get_db(cli_db, [ws, msgsnt])
    ch = get_db(cli_db, [ws, "chan"])
    if msntcnt >= get_db(cli_db, [ws, bpsize]):
        await put(ch, {op: bpwait,
                       payload: {"ws": ws, kwmsg: msg, "encode": encode,
                                 msgsnt: msntcnt}})
    else:
        hmsg = {op: kwmsg, payload: msg}
        await send(ws, encode, hmsg)
        update_db(cli_db, [ws, msgsnt], msntcnt+1)
        await put(ch, {op: sent,
                       payload: {"ws": ws, kwmsg: hmsg,
                                 msgsnt: get_db(cli_db, [ws, msgsnt])}})


async def receive (ws, msg):
    kwmsg = keyword("msg")
    mop = get_msg_op(msg)
    if mop == set:
        #print("INIT MSG: ", msg)
        mbpsize = msg[payload][bpsize]
        mmsgrcv = msg[payload][msgrcv]
        update_db(cli_db, [ws, msgrcv], mmsgrcv)
        update_db(cli_db, [ws, bpsize], mbpsize)
    elif mop == reset:
        update_db(cli_db, [ws, msgsnt], msg[payload][msgsnt])
        ch = get_db(cli_db, [ws, "chan"])
        await put(ch, {op: bpresume, payload: msg})
    elif mop == "msg" or mop == kwmsg:
        rcvd = get_db(cli_db, [ws, msgrcv])
        data = get_msg_payload(msg)
        if rcvd+1 >= get_db(cli_db, [ws, bpsize]):
            update_db(cli_db, [ws, msgrcv], 0)
            await send(ws, "binary", {op: reset, payload: {msgsnt: 0}})
        else:
            update_db(cli_db, [ws, msgrcv], get_db(cli_db, [ws, msgrcv])+1)
        ch = get_db(cli_db, [ws, "chan"])
        await put(ch, {op: kwmsg, payload: {"ws": ws, "data": data}})
    else:
        print("Client Receive Handler - unknown OP ", msg)


async def read_line (ws):
    try:
        msg = await ws.get_message()
        msg = msgpack.unpackb(msg, ext_hook=ext_hook, raw=False)
        update_db(cli_db, ['msg'], msg)
    except tws.ConnectionClosed as e:
        await rmtclose(ws,e)
        update_db(cli_db, ['msg'], stop)
    except Exception as e:
        await onerror(ws,e)
        update_db(cli_db, ['msg'], stop)

async def line_loop (ws):
    #print("Line loop called ...")
    while True:
        try:
            await read_line(ws)
            msg = get_db(cli_db, ['msg'])
            #print("MSG: ", msg)
            mop = get_msg_op(msg)
            if mop == "stop" or mop == keyword("stop"):
                ch = get_db(cli_db, [ws, "chan"])
                await put(ch, {op: stop, payload: {}})
                await ws.aclose()
                break
            else:
                await receive(ws, msg)
        except Exception as e:
            await onerror(ws,e)
            break
    #print("Line LOOP exit")


async def rmtclose (ws, e):
    ch = get_db(cli_db, [ws, "chan"])
    print("Close: {0}".format(e))
    await put(ch, {op: close, payload: {"code": e.code, "reason": e.reason}})

async def onerror (ws, e):
    ch = get_db(cli_db, [ws, "chan"])
    print("Error: ", e)
    await put(ch, {op: error, payload: {"ws": ws, "err": e}})




# 'ws://localhost:8765/ws'
async def connect (url, nursery):
    ws = await connect_websocket_url(nursery, url, message_queue_size=10)
    ch = chan(19)
    chrec = {"url": url, "ws": ws, "chan": ch,
             bpsize: 0, msgrcv: 0, msgsnt: 0}
    update_db(cli_db, ["ws"], ws)
    update_db(cli_db, [ch], chrec)
    update_db(cli_db, [ws], chrec)
    update_db(cli_db, [ws, "chan"], ch)
    await put(ch, {op: open, payload: ws})
    return chrec

async def open_connection (url, dispatchfn, apptask=None, appinfo={}):
    async with trio.open_nursery() as nursery:
        chrec = await connect(url, nursery)
        ch = chrec["chan"]
        ws = get_db(cli_db, [ch, 'ws'])
        nursery.start_soon(line_loop, ws)
        nursery.start_soon(goloop, ch, dispatchfn)
        await trio.sleep(0.1)
        if apptask != None:
            info = {"nursery": nursery, "chrec": chrec,
                    "ws": ws, "db": cli_db,
                    "appinfo": appinfo}
            nursery.start_soon(apptask, info)


async def main():
    try:
        async with open_websocket_url('ws://localhost:8765/ws') as ws:
            msg = await ws.get_message()
            msg = msgpack.unpackb(msg, ext_hook=ext_hook, raw=False)
            print('Received message: %s', msg)
            await send_msg(ws, {"op": "msg",
                                "payload": {op: "msg", "data": 'hello world!'}})
            msg = await ws.get_message()
            msg = msgpack.unpackb(msg, ext_hook=ext_hook, raw=False)
            print('Received message: %s', msg)
    except OSError as ose:
        print('Connection attempt failed: %s', ose)


#trio.run(main)


#        await send_msg(ws, {"op": "msg",
#                            "payload": {op: "msg", "data": 'hello world!'}})
