import trio


def chan (size=1):
    put,take = trio.open_memory_channel(size)
    return (put, take)

async def put (ch, x):
    sndch = ch[0]
    await sndch.send(x)

async def take (ch):
    rcvch = ch[1]
    x = await rcvch.receive()
    return x

