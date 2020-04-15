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


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.File;

import androidx.core.app.NotificationCompat;

class Notify {
    static void post( Context context, String data )
    {
        NotificationManager nm = (NotificationManager)
            context.getSystemService( Context.NOTIFICATION_SERVICE );
        String channelID  = getChannelID( context, nm );

        Intent intent = MainActivity.getSelfIntent( context );
        PendingIntent pi = getPendingIntent( context, intent );

        if ( 40 < data.length() ) {
            data = data.substring( 0, 40 );
        }
        
        NotificationCompat.Builder builder =
            new NotificationCompat.Builder( context, channelID )
            .setContentIntent( pi )
            .setSmallIcon( R.drawable.ic_launcher_background )
            .setAutoCancel( true )
            .setContentTitle( context.getString(R.string.notify_receipt_title) )
            .setContentText( context.getString( R.string.notify_receipt_body_fmt, data ) );
            ;

        Notification notification = builder.build();
        nm.notify( R.string.channel_desc, notification );
    }

    static void post( Context context, File newFile )
    {
        String data = String.format( "new file named %s", newFile.getName() );
        post( context, data );
    }

    private static String getChannelID( Context context, NotificationManager nm )
    {
        String name = String.format( "NOTIFY_%x", R.string.channel_desc );
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            NotificationChannel channel = nm.getNotificationChannel( name );
            if ( channel == null ) {
                String channelDesc = context.getString( R.string.channel_desc );

                channel = new NotificationChannel( name, channelDesc,
                                                   NotificationManager.IMPORTANCE_LOW );
                channel.enableVibration( true );
                nm.createNotificationChannel( channel );
            }
        }
        return name;
    }

    private static PendingIntent getPendingIntent( Context context, Intent intent )
    {
        PendingIntent pi = PendingIntent
            .getActivity( context, R.string.channel_desc, intent,
                          PendingIntent.FLAG_ONE_SHOT );
        return pi;
    }
}
