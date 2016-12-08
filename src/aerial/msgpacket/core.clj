(ns aerial.msgpacket.core
  )


(defprotocol msgPacket
  " All client server (or peer to peer as well) communication involves
    three different protocols:

    * The line data protocol - the encoding and decoding of all
      transmission information to and from sockets (or other 'port'
      artefacts)

    * The message packet protocol - the protocol defining the
      'envelope' and meta structure carrying the data payload of the
      message.

    * The data payload structure.

    The first of these is what line 'packer' protocols cover - such as
    the IPacker protocol in Sente or the ChordFormatter protocol in
    Chord.

    The last of these is application dependent - the what and how the
    data being used at the semantic level of the application

    The second is what this protocol is about. By defining the second
    as an explicit protocol, libs and frameworks are freed from
    complecting together the other two.
  "
  (std-events [_]
    "Return a msg protocol dependent map of the standard event id
     forms for the standard events for a msg protocol.

         Event             Map Key

     * open connection   :open-conn
     * close connection  :close-conn
     * close channel     :close-chan
     * channel timeout   :chan-timeout
     * channel error     :chan-error
     * bad packet        :bad-packet
     * bad event         :bad-event
     * pause stream      :pause
     * resume stream     :resume
     * end stream        :end

     Only the first three are required.
    ")

  (event? [_ x]
   "Return whether x is a legal event")

  (event-msg? [_ x]
   "Validate x is a legal msg packet")

  (assert-event [_ x]
   "Assert that x is a true event or throw exception")

  (event [_ x]
   "return event of msg packet")

  (connect-request [_ req]
   "Field a connect request and set up connection.  REQ is a ring
    request map, which contains all relevant data.  Packer is a packer
    instance which is able to decode the line data packet.")

  (enclose_ [this data]
   "Make event packet from data")

  (enclose-send_ [this chan data]
   "Enclose data and send msg to channel. Packer is the line data
    protocol packer for this msg packet type")

  (open_ [_ data]
   "Get msg packet from socket data")

  (receive-open [_ chan]
   "Receive data off channel and open. Packer is the line data
    protocol packer for this msg packet type."))

(defn enclose [this & data]
  (enclose_ this data))

(defn enclose-send [this chan & data]
  (enclose-send_ this chan data))

(defn open [this & data]
  (open_ this data))

