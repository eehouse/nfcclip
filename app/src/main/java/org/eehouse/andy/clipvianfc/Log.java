/* -*- compile-command: "find-and-gradle.sh inDeb"; -*- */
/*
 * Copyright 2020 by Eric House (xwords@eehouse.org).  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.eehouse.andy.clipvianfc;

import java.util.Formatter;

class Log {
    private static final boolean STORE = false;

    static void d( String tag, String fmt, Object... args )
    {
        String str = new Formatter().format( fmt, args ).toString();
        android.util.Log.d( tag, str );
        store( "d", tag, str );
    }

    static void e( String tag, String fmt, Object... args )
    {
        String str = new Formatter().format( fmt, args ).toString();
        android.util.Log.e( tag, str );
        store( "e", tag, str );
    }

    private static void store( String level, String tag, String msg )
    {
        if ( STORE ) {
        }
    }
}
