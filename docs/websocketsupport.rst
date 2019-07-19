WebSocket Support
=================

The OpenDXL Java Client supports connecting to DXL Brokers via WebSockets. The ``[BrokersWebSockets]`` section is
where the list of DXL Brokers that support WebSocket connections are listed in the ``dxlclient.config`` file.
The default protocol connection behavior is the following:

   1. MQTT will be used for connections to DXL Brokers if the ``[Brokers]`` section is not empty
   2. WebSockets will be used for connections to DXL Brokers if the the ``[Brokers]`` section is empty and the
      ``[BrokersWebSockets]`` section is not empty.

In the ``dxlclient.config`` the optional ``UseWebSockets`` setting under the ``[General]`` section is used to
override the default protocol connection behavior and indicate if the OpenDXL Java Client should connect to
DXL Brokers via WebSockets. To override the default behavior and force the OpenDXL Java Client to use WebSocket
connections to the list of DXL Brokers in the ``[BrokerWebSockets]`` section, set the ``UseWebSockets`` setting to
true in the ``dxlclient.config`` file:

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

          #[Proxy]
          #Address=proxy.mycompany.com
          #Port=3128
          #User=proxyUser
          #Password=proxyPassword

If the ``UseWebSockets`` setting is set to false, then the OpenDXL Java Client will connect to the list of
DXL Brokers in the ``[Brokers]`` section via MQTT.