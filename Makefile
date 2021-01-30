.PHONY : install srv_install srv client client_clean srv_clean clean all

all: srv client

install: srv_install client

srv:
	cd srv && ./configure && make

srv_install: srv
	cd srv && make install

client: client_clean
	cd client && make && ln -sf ./client/client ../sclient

client_clean: 
	cd client && make clean && rm -f ../sclient

srv_clean: 
	cd srv && make clean

clean: srv_clean client_clean
