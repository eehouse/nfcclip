/* -*- compile-command: "find-and-gradle.sh inFossDeb"; -*- */
/*
 * Copyright 2020 by Eric House (eehouse@eehouse.org).  All rights reserved.
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

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import android.widget.ListAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClipFragment extends PageFragment
    implements View.OnClickListener,
               ClipboardManager.OnPrimaryClipChangedListener,
               NFCUtils.Callbacks {
    private static final String TAG = ClipFragment.class.getSimpleName();
    private View mParentView;
    private ClipData.Item mClipData;
    private String[] mType = {null};
    private String[] mLabel = {null};

    @Override
    public void onResume() {
        super.onResume();
        showHideDisabled();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        mParentView = view;

        int[] buttons = { R.id.send, R.id.enable, R.id.file_send, R.id.file_choose, };
        for ( int id : buttons ) {
            view.findViewById(id).setOnClickListener( this );
        }

        ((TextView)view.findViewById(R.id.clip_text))
            .setMovementMethod(new ScrollingMovementMethod());

        view.getViewTreeObserver().
            addOnWindowFocusChangeListener(new ViewTreeObserver.OnWindowFocusChangeListener() {
                    @Override
                    public void onWindowFocusChanged(final boolean hasFocus)
                    {
                        if ( hasFocus ) {
                            getClipData();
                        }
                    }
                } );

        ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE))
                .addPrimaryClipChangedListener( this );
    }

    @Override
    public void onClick(View view)
    {
        int id = view.getId();
        switch ( id ) {
        case R.id.send:
            NFCUtils.sendClip( getActivity(), this, mType[0], mLabel[0], mClipData );
            break;
        case R.id.enable:
            Intent intent = new Intent( Settings.ACTION_NFC_SETTINGS )
                .addFlags( Intent.FLAG_ACTIVITY_NEW_DOCUMENT );
            getActivity().startActivity( intent );
            break;

        case R.id.file_send:
            TextView tv = (TextView)mParentView.findViewById(R.id.chosen_file);
            String desc = tv.getText().toString();
            File file = FilePickDialogFragment.getForDesc( desc );
            if ( null != file ) {
                NFCUtils.sendFile( getActivity(), this, file );
            }
            break;
        case R.id.file_choose:
            new FilePickDialogFragment( new FilePickDialogFragment.ItemSelProc() {
                    @Override
                    public void onItemSelected( String item ) {
                        TextView view = (TextView)mParentView.findViewById(R.id.chosen_file);
                        view.setText( item );
                    }
                } ).show( getFragmentManager(), "NoticeDialogFragment");
            break;

        default:
            Assert.fail();
        }
    }

    @Override
    public void onPrimaryClipChanged()
    {
        getClipData();
    }

    @Override
    public void onSendEnabled()
    {
        showSending( true );
    }

    @Override
    public void onSendComplete( boolean succeeded )
    {
        showSending( false );
        if ( succeeded ) {
            getClipData();
        }
    }

    @Override
    public void onProgressMade( final int cur, final int max )
    {
        getActivity().runOnUiThread( new Runnable() {
                @Override
                public void run() {
                    ProgressBar pb = (ProgressBar)mParentView.findViewById( R.id.progress );
                    pb.setMax(max);
                    pb.setProgress(cur);
                }
            } );
    }

    private void showSending( final boolean sending )
    {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mParentView.findViewById(R.id.send)
                    .setVisibility( sending ? View.GONE : View.VISIBLE );
                mParentView.findViewById(R.id.sending_status)
                    .setVisibility( !sending ? View.GONE : View.VISIBLE );
            }
        });
    }

    private void getClipData()
    {
        final Activity activity = getActivity();
        if ( null != activity ) {
            activity.runOnUiThread( new Runnable() {
                    @Override
                    public void run() {
                        mClipData = Clip.getData( activity, mType, mLabel );

                        TextView tv = (TextView)mParentView.findViewById(R.id.clip_label);
                        if ( null == mLabel[0] ) {
                            tv.setVisibility( View.GONE );
                        } else {
                            tv.setVisibility( View.VISIBLE );
                            tv.setText( getString( R.string.labelLabel, mLabel[0] ) );
                        }
                        tv = (TextView)mParentView.findViewById(R.id.clip_type);
                        tv.setText( getString( R.string.typeLabel, mType[0] ) );

                        if (null != mClipData) {
                            tv = (TextView) mParentView.findViewById(R.id.clip_text);
                            tv.setText( mClipData.coerceToText(activity) );
                        }

                        mParentView.findViewById(R.id.send).setEnabled( mClipData != null );
                    }
                } );
        }
    }

    private void showHideDisabled()
    {
        boolean enabled = NFCUtils.nfcEnabled( getContext() );
        mParentView.findViewById( R.id.disabled_expl )
            .setVisibility( enabled ? View.GONE : View.VISIBLE );
        mParentView.findViewById( R.id.send )
            .setVisibility( !enabled ? View.GONE : View.VISIBLE );
    }

    public static class FilePickDialogFragment extends DialogFragment {

        interface ItemSelProc {
            void onItemSelected( String item );
        }
        ItemSelProc mProc;

        public FilePickDialogFragment( ItemSelProc proc )
        {
            super();
            mProc = proc;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState )
        {
            final ListAdapter adapter = listFiles();
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder
                .setAdapter( adapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick( DialogInterface dialogInterface, int ii ) {
                            String item = (String)adapter.getItem(ii);
                            mProc.onItemSelected( item );
                        }
                    })
                .setNegativeButton( android.R.string.cancel, null )
                ;

            return builder.create();
        }

        public static File getForDesc( String desc )
        {
            Log.d( TAG, "getForDesc(%s)", desc );
            File result = null;
            String[] parts = TextUtils.split( desc, ":" );
            if ( 2 <= parts.length ) {
                String[] pathParts = Arrays.copyOfRange( parts, 0, parts.length - 1 );
                String name = TextUtils.join(":", pathParts );
                File dir = getFilesDir();
                result = new File( dir, name );
                Log.d( TAG, "getForDesc(%s) => %s", desc, result );
            }
            return result;
        }

        private static String descForFile( File file )
        {
            long len = file.length();
            String name = file.getName();
            String entry = String.format("%s:%d bytes", name, len);
            return entry;
        }

        private ArrayAdapter<String> listFiles()
        {
            List<String> fileList = new ArrayList<>();
            Log.d( TAG, "calling getExternalStorageDirectory()" );
            File dir = getFilesDir();
            File[] files = dir.listFiles();
            if ( null == files ) {
                Log.e( TAG, "got nothing from getExternalStorageDirectory()" );
            } else {
                for ( File file : files ) {
                    if ( file.isFile() && file.canRead() ) {
                        long len = file.length();
                        if ( len <= Integer.MAX_VALUE ) {
                            fileList.add( descForFile(file) );
                        }
                    }
                }
            }

            String[] fileDescs = fileList.toArray( new String[fileList.size()] );
            ArrayAdapter<String> adapter
                = new ArrayAdapter<>( getContext(),
                                      android.R.layout.simple_spinner_item,
                                      fileDescs );
            return adapter;
        }
    }

    static File getFilesDir()
    {
        File dir = Environment.getExternalStorageDirectory();
        dir = new File( dir, Environment.DIRECTORY_DOWNLOADS );
        return dir;
    }

}
