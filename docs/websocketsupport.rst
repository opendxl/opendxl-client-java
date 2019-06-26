WebSocket Support
=================

The OpenDXL Java Client will connect to DXL Brokers via WebSockets when the ``UseWebSockets`` setting is set to ``true``
in the ``dxlclient.config`` file.

       .. parsed-literal::

          [General]
          UseWebSockets=true

          [Certs]
          BrokerCertChain=c:\\certificates\\brokercerts.crt
          CertFile=c:\\certificates\\client.crt
          PrivateKey=c:\\certificates\\client.key

          [Brokers]
          {5d73b77f-8c4b-4ae0-b437-febd12facfd4}={5d73b77f-8c4b-4ae0-b437-febd12facfd4};8883;mybroker.mcafee.com;192.168.1.12
          {24397e4d-645f-4f2f-974f-f98c55bdddf7}={24397e4d-645f-4f2f-974f-f98c55bdddf7};8883;mybroker2.mcafee.com;192.168.1.13

          [BrokersWebSockets]
          {5d73b77f-8c4b-4ae0-b437-febd12facfd4}={5d73b77f-8c4b-4ae0-b437-febd12facfd4};443;mybroker.mcafee.com;192.168.1.12
          {24397e4d-645f-4f2f-974f-f98c55bdddf7}={24397e4d-645f-4f2f-974f-f98c55bdddf7};443;mybroker2.mcafee.com;192.168.1.13

          [Proxy]
          Address=proxy.mycompany.com
          Port=3128
          User=proxyUser
          Password=proxyPassword


When the ``UseWebSockets`` setting is set to ``true``, the OpenDXL Java Client will connect to the DXL Brokers
listed in the ``[BrokersWebSockets]`` sections via WebSockets.

If ``UseWebSockets`` setting is set to ``false`` or does not exist in the ``dxlclient.config`` file, then the
OpenDXL Java Client will connect to the DXL Brokers listed in the ``[Brokers]`` section via MQTT.