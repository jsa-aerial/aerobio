"""
Extends the synchronisation objects of asyncio (e.g. Lock, Event,
Condition, Semaphore, Queue) with Channels like in Go.  Channels can
be used for asynchronous or synchronous (blocking handshake, no order)
message exchange.  select() can be used to react on finished
await-calls and thus also on sending or receiving with channels.  The
helper go() provides a simple way to schedule the concurrent functions
in an event loop of a different thread.

from awaitchannel import Chan, select, go, ChannelClosed, loop

To run a blocking function in a background thread and get an awaitable
future for it, use the run_in_executor method of the loop:

    f = loop.run_in_executor(None, normal_longrunning_function)
    res = await f

Note that you need to pass the .loop attribute of this module when you
are using functions provided by asyncio yourself.
"""
import asyncio


class Chan:
  """
  Go-style channel with await send/recv can also be used as an
  iterator which is calling recv() until a ChannelClosed exception
  occurs
  """
  q = None  # data channel
  x = None  # sync channel for size=0
  size = None
  is_closed = False
  closed = "{}{}".format(hash("Chan.closed"), "Chan.closed")  # magic string as last element
  def __init__(self, size=0):
    """size 0 indicates a synchronous channel (handshake)
    size -1 indicates an unlimited buffer size
    otherwise send will block when buffer size is reached"""
    if size == 0:
      self.q = asyncio.Queue(0, loop=loop)
      self.x = asyncio.Queue(0, loop=loop)
    elif size == -1:
      self.q = asyncio.Queue(0, loop=loop)
    else:
      self.q = asyncio.Queue(size, loop=loop)
    self.size = size

    def __eq__(self, x):
      return type(x) is Chan and self.q == x.q
    def __hash__(self):
      return hash(self.q)


  @asyncio.coroutine
  def close(self):
    """closes the channel which leads to a failure at the recv side if empty and disallows further sending"""
    self.is_closed = True
    if not self.q.full():
      self.q.put_nowait(self.closed)

  @asyncio.coroutine
  def send(self, item):
    """blocks if size=0 until there is a recv and this send operation was chosen
    blocks if send was used <size> times without a recv
    blocks never for size=-1"""
    if self.is_closed:
      raise ChannelClosed
    if self.size == 0:
      yield from self.x.get()
      # randomizing item with those of other pending sends is not necessary because of x.get()
    yield from self.q.put(item)

  def send_ready(self):
    return not self.q.full()

  def recv_ready(self):
    return not self.q.empty()

  @asyncio.coroutine
  def recv(self):
    """blocks until something is available
    fails if channel is closed after all is processed"""
    if self.size == 0:
      yield from self.x.put(True)
    if self.is_closed:
      if self.q.empty():
        self.q.put_nowait(self.closed)
        raise ChannelClosed
      if not self.q.full():
        self.q.put_nowait(self.closed)
    g = yield from self.q.get()
    if self.is_closed and g == self.closed:
      if not self.q.full():
        self.q.put_nowait(self.closed)  # push back for others
      raise ChannelClosed
    return g

  async def __aiter__(self):
    return self

  async def __anext__(self):
    try:
      return await self.recv()
    except ChannelClosed:
      raise StopAsyncIteration



class ChannelClosed(Exception):
  pass


### select on await events

async def wrap_future(e, f):
  return e, await f

class SelectTasks:
  """helper class used for (pending) await-tasks monitored by select()"""
  tasks = []
  completed = []
  def __init__(self, futures_list=None, already_running=False, completed=[]):
    if futures_list and not already_running:
      self.extend(futures_list)
    elif futures_list and already_running:
      self.tasks = list(futures_list)
    else:
      self.tasks = []
    self.completed = completed

  def append(self, a):
    e, f = a
    self.tasks.append(wrap_future(e, f))

  def extend(self, futures_list):
    self.tasks.extend([wrap_future(e, f) for e, f in futures_list])

  def __bool__(self):
    return bool(self.tasks) or bool(self.completed)

  def __len__(self):
    return len(self.tasks) + len(self.completed)


@asyncio.coroutine
def select(futures_list):
  """
  parameter: select on a list of identifier-await-tuples like ['r', c.recv()), (c, c.send(2))]
  returns: a tuple consiting of an identifier-result-tuple like ('r', 7) or (c, None) and
  a special list object of pending tasks which can be directly used for the next select call or even expanded/appended on before

  Be aware that the results are internally buffered when more complete at the same time and thus the logical ordering can be different.
  """
  if type(futures_list) is not SelectTasks:
    futures_list = SelectTasks(futures_list)
  if futures_list.completed:
    result = futures_list.completed.pop()
    return result, futures_list
  done, running = yield from asyncio.wait(futures_list.tasks, return_when=asyncio.FIRST_COMPLETED, loop=loop)
  result = done.pop().result()
  results = [r.result() for r in done]
  return result, SelectTasks(running, already_running=True, completed=results)


# short helper functions

import threading
import atexit

count_tasks = 0
def counter(i=0):
  global count_tasks
  count_tasks += i
  return count_tasks


# This thread's loop will be used - unfortunately needs to be passed
# everywhere as the execution takes place in a background thread.
# If you do only use Chan() and select() and not go(), you can
# omit this by forcing it to None if it causes trouble.
loop = asyncio.get_event_loop()

loops = {'running': [], 'stopped': []}

def init_event_loops ():
  for i in range(10):
    loops['stopped'].append(asyncio.new_event_loop())
    #loops['stopped'].append(asyncio.get_event_loop())
    for i,el in enumerate(loops['stopped']):

      atexit.register(el.close)

def s2r ():
  if len(loops['stopped']) > 0:
    el = loops['stopped'].pop()
    loops['running'].append(el)
    return el
  else:
    nel = asyncio.get_event_loop()
    atexit.register(nel.close)
    loops['running'].append(nel)
    return nel

def r2s (el):
  if el.is_running():
    el.call_soon(evl.stop)
  loops['stopped'].append(el)

def sweep_r2s ():
  for i,el in enumerate(loops['running']):
    el.call_soon_threadsafe(el.stop)
    #el.call_soon(el.stop)
    loops['running'].remove(el)
    loops['stopped'].append(el)


def cor_runit (f, *args, **kwargs):
  el = s2r()

  async def runit ():
    x = await f(*args, **kwargs)
    el.call_soon_threadsafe(el.stop)
    #el.call_soon(el.stop)
    return x

  f = asyncio.run_coroutine_threadsafe(runit(), el)
  print("EL is running:", el.is_running())
  threading.Thread(name='worker', target=el.run_forever).start()
  return f
  #x = f.result()
  #return x


import time
async def foo (n):
  time.sleep(n)
  return n


init_event_loops()



def go(f, *args, **kwargs):
  """schedule an async function on the asyncio event loop of the worker thread
  returns a concurrent.future which has a (non-await) blocking .result() method to wait until the result of f() is returned"""

  if 'evloop' in kwargs:
    evl = kwargs['evloop']
    del kwargs['evloop']
  else:
    evl = loop

  async def cleanup():
    x = await f(*args, **kwargs)
    counter(-1)
    if counter() == 0:
      evl.call_soon(evl.stop)
    return x

  r = asyncio.run_coroutine_threadsafe(cleanup(), evl)
  counter(+1)

  if not evl.is_running():
    threading.Thread(name='eventloop-worker', target=evl.run_forever).start()
  return r
