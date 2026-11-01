#!/usr/bin/env sh
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`/"$link"
    fi
done
saved=""`pwd`""
cd "`dirname "$PRG"`" >/dev/null
APP_HOME="`pwd`"
cd "$saved" >/dev/null
exec java -Xmx64m -Xms64m -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
