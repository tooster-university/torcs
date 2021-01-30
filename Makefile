.PHONY : install srv_install srv client clean all

all: srv client

install: srv_install client

srv:
	cd srv && ./configure && make

srv_install: srv
	cd srv && make install

client:
	cd client && make && ln -sf ./client/client ../sclient

clean:
	cd srv && make clean
	cd client && make clean && rm -f ../sclient
