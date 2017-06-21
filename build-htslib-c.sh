#!/usr/bin/env bash
HTSLIBVER=1.4.1
HTSLIB=htslib-$HTSLIBVER
set -e
mkdir -p work
cd work
if [ ! -d $HTSLIB ]; then
    curl -LO https://github.com/samtools/htslib/releases/download/$HTSLIBVER/$HTSLIB.tar.bz2
    tar xjf $HTSLIB.tar.bz2
fi
cd $HTSLIB

# export compilation flags if using macports
if [ -d "/opt/local/include" ] && [ -d "/opt/local/lib" ]; then
    export CFLAGS="-I/opt/local/include -I/opt/local/include/openssl"
    export LDLIBS=-L/opt/local/lib
fi

./configure --enable-libcurl --prefix=$PWD/../htslib
make
make install
