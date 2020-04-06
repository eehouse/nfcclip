#!/bin/sh

set -e -u

URL=''
VARIANT=''

usage() {
	[ $# -gt 0 ] && echo "ERROR: $1"
	echo "usage: $0 --variant variantDirName --url URL"

	echo "I use this to generate qrcode drawables as needed. "
	echo "Since they're checked in it's not"
	echo "currently part of the build system. That could change."

	exit 1
}


while [ $# -gt 0 ]; do
	case $1 in
		--variant)
			[ $# -gt 1 ] || usage "$1 requires a parameter"
			VARIANT=$2
			shift
			;;
		--url)
			[ $# -gt 1 ] || usage "$1 requires a parameter"
			URL="$2"
			shift
			;;
		--help|*)
			usage
			;;
	esac
	shift
done

[ -z $URL ] && usage "--url is a required parameter"
[ -z $VARIANT ] && usage "--variant is a required parameter"

WD=$(cd $(dirname $0) && pwd)
cd $WD

OUTFILE="./app/src/${VARIANT}/res/drawable/qrcode.png"
mkdir -p $(dirname $OUTFILE)

qrencode -o $OUTFILE -t PNG -s 5 "${URL}"

echo "SUCCESS. Here's your file"
ls -l $OUTFILE
