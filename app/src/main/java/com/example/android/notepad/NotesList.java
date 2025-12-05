/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Objects;
import android.text.TextUtils;


/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler} or
 * {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NotesList extends ListActivity {

    // For logging and debugging
    private static final String TAG = "NotesList";
    
    // 权限请求常量
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final int REQUEST_EXPORT_ALL = 1002;
    private static final int REQUEST_EXPORT_SINGLE = 1003;
    private Uri pendingExportUri = null;

    /**
     * The columns needed by the cursor adapter
     */
    private static final String[] PROJECTION = new String[]{
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_COLOR, // 添加颜色列
            NotePad.Notes.COLUMN_NAME_NOTE, // 添加内容列
            NotePad.Notes.COLUMN_NAME_CATEGORY // 添加分类列
    };
    /**
     * The index of the title column
     */
    private static final int COLUMN_INDEX_TITLE = 1;
    
    // 当前选中的分类
    private String mCurrentCategory = null;

    /**
     * onCreate is called when Android starts this Activity from scratch.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        // Gets the intent that started this Activity.
        Intent intent = getIntent();

        // If there is no data associated with the Intent, sets the data to the default URI, which
        // accesses a list of notes.
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        /*
         * Sets the callback for context menu activation for the ListView. The listener is set
         * to be this Activity. The effect is that context menus are enabled for items in the
         * ListView, and the context menu is handled by a method in NotesList.
         */
        getListView().setOnCreateContextMenuListener(this);

        /* 使用 getContentResolver().query() 替代废弃的 managedQuery()
         *
         * Please see the introductory note about performing provider operations on the UI thread.
         */
        Cursor cursor = getContentResolver().query(
                getIntent().getData(),            // Use the default content URI for the provider.
                PROJECTION,                       // Return the note ID and title for each note.
                null,                             // No where clause, return all records.
                null,                             // No where clause, therefore no where column values.
                NotePad.Notes.DEFAULT_SORT_ORDER  // Use the default sort order.
        );
        
        // 安全地输出列名日志
        if (cursor != null) {
            Log.d("NotesList", "columns = " + Arrays.toString(cursor.getColumnNames()));
        } else {
            // Cursor 为 null 时，创建一个空的 adapter
            setListAdapter(new SimpleCursorAdapter(this, R.layout.noteslist_item, null, new String[]{}, new int[]{}));
            return;
        }
        
// 1. 新的映射：标题 + 内容 + 时间
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_NOTE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
        };
        int[] viewIDs = {
                android.R.id.text1,     // 标题
                R.id.note_content,      // 内容
                R.id.time_stamp         // 时间戳
        };

// 2. 创建 Adapter
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                R.layout.noteslist_item,
                cursor,
                dataColumns,
                viewIDs,
                0);
        
        // 确保 Cursor 能接收到数据变化通知
        cursor.setNotificationUri(getContentResolver(), getIntent().getData());
        
// 3. 关键：ViewBinder 把 long 时间转成可读字符串
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view.getId() == R.id.time_stamp) {          // 只处理时间那一列
                    long t = cursor.getLong(columnIndex);
                    String txt = new java.text.SimpleDateFormat(
                            "yyyy-MM-dd  HH:mm",
                            java.util.Locale.CHINA).format(new java.util.Date(t));
                    ((TextView) view).setText(txt);
                    return true;                                // 我亲自处理了
                }
                if (view.getId() == android.R.id.text1) {
                    // 先设置标题文本
                    String title = cursor.getString(columnIndex);
                    ((TextView) view).setText(title);
                    
                    // 然后处理颜色 - 只给卡片的父容器设置背景色
                    int colorColIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_COLOR);
                    if (colorColIndex != -1) {
                        int color = cursor.getInt(colorColIndex);
                        // 获取CardView并设置背景色
                        android.view.View parent = (android.view.View) view.getParent();
                        if (parent != null && parent.getParent() instanceof androidx.cardview.widget.CardView) {
                            androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) parent.getParent();
                            if (color == 0) {
                                cardView.setCardBackgroundColor(0xFFFFFFFF);
                            } else {
                                cardView.setCardBackgroundColor(color);
                            }
                        }
                    }
                    return true;
                }
                if (view.getId() == R.id.note_content) {
                    // 处理内容视图
                    String content = cursor.getString(columnIndex);
                    // 限制内容显示长度
                    if (content != null && content.length() > 100) {
                        content = content.substring(0, 100) + "...";
                    }
                    ((TextView) view).setText(content);
                    return true;
                }
                return false;                                   // 其余交给系统
            }
        });

// 4. 挂上 ListView
        setListAdapter(adapter);

        // 启用返回按钮
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回列表时重新查询数据，确保显示最新内容
        refreshNotesList();
    }
    
    /**
     * 刷新笔记列表
     */
    private void refreshNotesList() {
        try {
            // 重新查询数据
            Cursor cursor = getContentResolver().query(
                    getIntent().getData(),            // Use the default content URI for the provider.
                    PROJECTION,                       // Return the note ID and title for each note.
                    null,                             // No where clause, return all records.
                    null,                             // No where clause, therefore no where column values.
                    NotePad.Notes.DEFAULT_SORT_ORDER  // Use the default sort order.
            );
            
            // 确保新 Cursor 能接收到数据变化通知
            if (cursor != null) {
                cursor.setNotificationUri(getContentResolver(), getIntent().getData());
            }
            
            // 更新适配器中的 Cursor
            SimpleCursorAdapter adapter = (SimpleCursorAdapter) getListAdapter();
            if (adapter != null) {
                adapter.changeCursor(cursor);
            }
        } catch (Exception e) {
            // 忽略刷新异常，避免崩溃
            Log.e("NotesList", "Error refreshing list", e);
        }
    }

    /**
     * Called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     * <p>
     * Sets up a menu that provides the Insert option plus a list of alternative actions for
     * this Activity. Other applications that want to handle notes can "register" themselves in
     * Android by providing an intent filter that includes the category ALTERNATIVE and the
     * mimeTYpe NotePad.Notes.CONTENT_TYPE. If they do this, the code in onCreateOptionsMenu()
     * will add the Activity that contains the intent filter to its list of options. In effect,
     * the menu will offer the user other applications that can handle notes.
     *
     * @param menu A Menu object, to which menu items should be added.
     * @return True, always. The menu should be displayed.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // Generate any additional actions that can be performed on the
        // overall list.  In a normal install, there are no additional
        // actions found here, but this allows other applications to extend
        // our menu with their own actions.
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        // 搜索按钮（常驻标题栏）
        menu.add(0, Menu.FIRST + 1, 0, "搜索")
                .setIcon(android.R.drawable.ic_menu_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // 重置“全部”按钮（溢出菜单）
        menu.add(0, Menu.FIRST + 2, 0, "显示全部")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // 分类按钮
        menu.add(0, Menu.FIRST + 4, 0, "分类")
                .setIcon(android.R.drawable.ic_menu_agenda)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return super.onCreateOptionsMenu(menu);

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // The paste menu item is enabled if there is data on the clipboard.
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        // If the clipboard contains an item, enables the Paste option on the menu.
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            // If the clipboard is empty, disables the menu's Paste option.
            mPasteItem.setEnabled(false);
        }

        // Gets the number of notes currently being displayed.
        final boolean haveItems = getListAdapter().getCount() > 0;

        // If there are any notes in the list (which implies that one of
        // them is selected), then we need to generate the actions that
        // can be performed on the current selection.  This will be a combination
        // of our own specific actions along with any extensions that can be
        // found.
        if (haveItems) {

            // This is the selected item.
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // Creates an array of Intents with one element. This will be used to send an Intent
            // based on the selected menu item.
            Intent[] specifics = new Intent[1];

            // Sets the Intent in the array to be an EDIT action on the URI of the selected note.
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // Creates an array of menu items with one element. This will contain the EDIT option.
            MenuItem[] items = new MenuItem[1];

            // Creates an Intent with no specific action, using the URI of the selected note.
            Intent intent = new Intent(null, uri);

            /* Adds the category ALTERNATIVE to the Intent, with the note ID URI as its
             * data. This prepares the Intent as a place to group alternative options in the
             * menu.
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * Add alternatives to the menu
             */
            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE,  // Add the Intents as options in the alternatives group.
                    Menu.NONE,                  // A unique item ID is not required.
                    Menu.NONE,                  // The alternatives don't need to be in order.
                    null,                       // The caller's name is not excluded from the group.
                    specifics,                  // These specific options must appear first.
                    intent,                     // These Intent objects map to the options in specifics.
                    Menu.NONE,                  // No flags are required.
                    items                       // The menu items generated from the specifics-to-
                    // Intents mapping
            );
            // If the Edit menu item exists, adds shortcuts for it.
            if (items[0] != null) {

                // Sets the Edit menu item shortcut to numeric "1", letter "e"
                items[0].setShortcut('1', 'e');
            }
        } else {
            // If the list is empty, removes any existing alternative actions from the menu
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }

        // Displays the menu
        return true;
    }

    /**
     * This method is called when the user selects an option from the menu, but no item
     * in the list is selected. If the option was INSERT, then a new Intent is sent out with action
     * ACTION_INSERT. The data from the incoming Intent is put into the new Intent. In effect,
     * this triggers the NoteEditor activity in the NotePad application.
     * <p>
     * If the item was not INSERT, then most likely it was an alternative option from another
     * application. The parent method is called to process the item.
     *
     * @param item The menu item that was selected by the user
     * @return True, if the INSERT menu item was selected; otherwise, the result of calling
     * the parent method.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()).setClassName(/* TODO: provide the application ID. For example: */ getPackageName(), "com.example.android.notepad.NoteEditor"));
            return true;
        } else if (id == R.id.menu_paste) {
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()).setClassName(/* TODO: provide the application ID. For example: */ getPackageName(), "com.example.android.notepad.NoteEditor"));
            return true;
        } else if (id == Menu.FIRST + 1) {          // 搜索
            android.widget.SearchView sv = new android.widget.SearchView(this);
            sv.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String s) {
                    doSearch(s);
                    return true;
                }
                @Override public boolean onQueryTextChange(String s) {
                    //doSearch(s);
                    return true;
                }
            });
            new android.app.AlertDialog.Builder(this)
                    .setTitle("输入关键词")
                    .setView(sv)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 获取搜索视图中的文本并执行搜索
                            CharSequence query = sv.getQuery();
                            doSearch(query != null ? query.toString() : "");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        } else if (id == Menu.FIRST + 2) {          // 全部
            mCurrentCategory = null;
            doSearch("");
            return true;
        } else if (id == Menu.FIRST + 4) {          // 分类
            showCategoryDialog();
            return true;
        } else if (id == R.id.menu_export) {        // 导出为TXT
            requestStoragePermission(REQUEST_EXPORT_ALL);
            return true;
        } else if (id == android.R.id.home) {  // 处理返回按钮
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doSearch(String key) {
        String selection = TextUtils.isEmpty(key) ? null :
                NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR " +
                        NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?";
        String[] args = TextUtils.isEmpty(key) ? null :
                new String[]{"%" + key + "%", "%" + key + "%"};
        Cursor c = getContentResolver().query(
                Objects.requireNonNull(getIntent().getData()),
                PROJECTION,
                selection,
                args,
                NotePad.Notes.DEFAULT_SORT_ORDER);
        SimpleCursorAdapter a = (SimpleCursorAdapter) getListAdapter();
        a.changeCursor(c);
        
        // 确保新 Cursor 也能接收到数据变化通知
        c.setNotificationUri(getContentResolver(), getIntent().getData());
    }

    /**
     * 导出所有笔记为TXT文件
     */
    private void exportNotesToTxt() {
        // 查询所有笔记
        Cursor cursor = getContentResolver().query(
                getIntent().getData(),
                new String[]{
                        NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_NOTE,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                },
                null,
                null,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        if (cursor == null) {
            Toast.makeText(this, "无法获取笔记数据", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cursor.getCount() == 0) {
            Toast.makeText(this, "没有笔记可以导出", Toast.LENGTH_SHORT).show();
            cursor.close();
            return;
        }

        // 构建要保存的文本内容
        StringBuilder content = new StringBuilder();
        content.append("笔记导出文件\n");
        content.append("导出时间: ").append(new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.CHINA).format(new java.util.Date())).append("\n\n");

        while (cursor.moveToNext()) {
            String title = cursor.getString(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE));
            String note = cursor.getString(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE));
            long modified = cursor.getLong(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));

            String modifiedStr = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.CHINA).format(new java.util.Date(modified));

            content.append("标题: ").append(title).append("\n");
            content.append("修改时间: ").append(modifiedStr).append("\n");
            content.append("内容:\n").append(note).append("\n");
            content.append("----------------------------------------\n\n");
        }

        cursor.close();

        // 保存文件
        saveTxtFile(content.toString());
    }

    /**
     * 保存TXT文件
     */
    private void saveTxtFile(String content) {
        try {
            // 检查存储权限
            if (!hasStoragePermission()) {
                Toast.makeText(this, "请授予存储权限以导出文件", Toast.LENGTH_LONG).show();
                return;
            }
            
            // 获取外部存储目录
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Toast.makeText(this, "无法创建目录", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // 创建文件名
            String fileName = "notes_export_" + new java.text.SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    java.util.Locale.CHINA).format(new java.util.Date()) + ".txt";
            
            File file = new File(dir, fileName);
            
            // 写入文件
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();
            
            // 通知用户文件已保存
            Toast.makeText(this, "笔记已导出到: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            
            // 通知系统扫描文件
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(file);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    /**
     * 导出单个笔记为TXT文件
     */
    private void exportSingleNoteToTxt(Uri noteUri) {
        // 查询单个笔记
        Cursor cursor = getContentResolver().query(
                noteUri,
                new String[]{
                        NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_NOTE,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                },
                null,
                null,
                null
        );
        
        if (cursor == null) {
            Toast.makeText(this, "无法获取笔记数据", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (cursor.getCount() == 0) {
            Toast.makeText(this, "没有找到笔记", Toast.LENGTH_SHORT).show();
            cursor.close();
            return;
        }
        
        cursor.moveToFirst();
        String title = cursor.getString(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE));
        String note = cursor.getString(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE));
        long modified = cursor.getLong(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
        
        cursor.close();
        
        // 构建要保存的文本内容
        StringBuilder content = new StringBuilder();
        content.append("笔记导出文件\n");
        content.append("导出时间: ").append(new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.CHINA).format(new java.util.Date())).append("\n\n");
        
        String modifiedStr = new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.CHINA).format(new java.util.Date(modified));
        
        content.append("标题: ").append(title).append("\n");
        content.append("修改时间: ").append(modifiedStr).append("\n");
        content.append("内容:\n").append(note).append("\n");
        
        // 保存文件，使用笔记标题作为文件名
        saveSingleNoteTxtFile(content.toString(), title);
    }
    
    /**
     * 保存单个笔记TXT文件
     */
    private void saveSingleNoteTxtFile(String content, String noteTitle) {
        try {
            // 检查存储权限
            if (!hasStoragePermission()) {
                Toast.makeText(this, "请授予存储权限以导出文件", Toast.LENGTH_LONG).show();
                return;
            }
            
            // 获取外部存储目录
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Toast.makeText(this, "无法创建目录", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // 创建文件名（清理标题中的非法字符）
            String cleanTitle = noteTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = cleanTitle + "_" + new java.text.SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    java.util.Locale.CHINA).format(new java.util.Date()) + ".txt";
            
            File file = new File(dir, fileName);
            
            // 写入文件
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();
            
            // 通知用户文件已保存
            Toast.makeText(this, "笔记已导出到: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            
            // 通知系统扫描文件
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(file);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    /**
     * 请求存储权限
     */
    private void requestStoragePermission(int requestCode) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                }, requestCode);
                return;
            }
        }
        
        // 如果已经有权限，直接执行导出操作
        handleExportAfterPermission(requestCode);
    }
    
    /**
     * 权限请求结果处理
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_EXPORT_ALL || requestCode == REQUEST_EXPORT_SINGLE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，执行导出操作
                handleExportAfterPermission(requestCode);
            } else {
                // 权限被拒绝
                Toast.makeText(this, "需要存储权限才能导出文件", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    /**
     * 权限授予后执行导出操作
     */
    private void handleExportAfterPermission(int requestCode) {
        if (requestCode == REQUEST_EXPORT_ALL) {
            exportNotesToTxt();
        } else if (requestCode == REQUEST_EXPORT_SINGLE && pendingExportUri != null) {
            exportSingleNoteToTxt(pendingExportUri);
            pendingExportUri = null;
        }
    }
    
    /**
     * 检查存储权限
     */
    private boolean hasStoragePermission() {
        // 对于Android 6.0及以上版本，需要动态请求权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                   checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true; // 低版本默认有权限
    }

    /**
     * This method is called when the user context-clicks a note in the list. NotesList registers
     * itself as the handler for context menus in its ListView (this is done in onCreate()).
     * <p>
     * The only available options are COPY and DELETE.
     * <p>
     * Context-click is equivalent to long-press.
     *
     * @param menu     A ContexMenu object to which items should be added.
     * @param view     The View for which the context menu is being constructed.
     * @param menuInfo Data associated with view.
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        // Tries to get the position of the item in the ListView that was long-pressed.
        try {
            // Casts the incoming data object into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * Gets the data associated with the item at the selected position. getItem() returns
         * whatever the backing adapter of the ListView has associated with the item. In NotesList,
         * the adapter associated all of the data for a note with its list item. As a result,
         * getItem() returns that data as a Cursor.
         */
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        // If the cursor is empty, then for some reason the adapter can't get the data from the
        // provider, so returns null to the caller.
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // Append to the
        // menu items for any other activities that can do stuff with it
        // as well.  This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id)));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * This method is called when the user selects an item from the context menu
     * (see onCreateContextMenu()). The only menu items that are actually handled are DELETE and
     * COPY. Anything else is an alternative option, for which default handling should be done.
     *
     * @param item The selected menu item
     * @return True if the menu item was DELETE, and no default processing is need, otherwise false,
     * which triggers the default handling of the item.
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        /*
         * Gets the extra info from the menu item. When an note in the Notes list is long-pressed, a
         * context menu appears. The menu items for the menu automatically get the data
         * associated with the note that was long-pressed. The data comes from the provider that
         * backs the list.
         *
         * The note's data is passed to the context menu creation routine in a ContextMenuInfo
         * object.
         *
         * When one of the context menu items is clicked, the same data is passed, along with the
         * note ID, to onContextItemSelected() via the item parameter.
         */
        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e);

            // Triggers default processing of the menu item.
            return false;
        }
        // Appends the selected note's ID to the URI sent with the incoming Intent.
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * Gets the menu item's ID and compares it to known actions.
         */
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri).setClassName(/* TODO: provide the application ID. For example: */ getPackageName(), "com.example.android.notepad.NoteEditor"));
            return true;
        } else if (id == R.id.context_copy) { //BEGIN_INCLUDE(copy)
            // Gets a handle to the clipboard service.
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            // Copies the notes URI to the clipboard. In effect, this copies the note itself
            assert clipboard != null;
            clipboard.setPrimaryClip(ClipData.newUri(   // new clipboard item holding a URI
                    getContentResolver(),               // resolver to retrieve URI info
                    "Note",                             // label for the clip
                    noteUri));                          // the URI

            // Returns to the caller and skips further processing.
            return true;
            //END_INCLUDE(copy)
        } else if (id == R.id.context_delete) {
            // Deletes the note from the provider by passing in a URI in note ID format.
            // Please see the introductory note about performing provider operations on the
            // UI thread.
            getContentResolver().delete(
                    noteUri,  // The URI of the provider
                    null,     // No where clause is needed, since only a single note ID is being
                    // passed in.
                    null      // No where clause is used, so no where arguments are needed.
            );

            // Returns to the caller and skips further processing.
            return true;
        } else if (id == R.id.context_export) {
            // 导出单个笔记为TXT
            pendingExportUri = noteUri;
            requestStoragePermission(REQUEST_EXPORT_SINGLE);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * This method is called when the user clicks a note in the displayed list.
     * <p>
     * This method handles incoming actions of either PICK (get data from the provider) or
     * GET_CONTENT (get or create data). If the incoming action is EDIT, this method sends a
     * new Intent to start NoteEditor.
     *
     * @param l        The ListView that contains the clicked item
     * @param v        The View of the individual item
     * @param position The position of v in the displayed list
     * @param id       The row ID of the clicked item
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // Constructs a new URI from the incoming URI and the row ID
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        // Gets the action from the incoming Intent
        String action = getIntent().getAction();

        // Handles requests for note data
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {

            // Sets the result to return to the component that called this Activity. The
            // result contains the new URI
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {

            // Sends out an Intent to start an Activity that can handle ACTION_EDIT. The
            // Intent's data is the note ID URI. The effect is to call NoteEdit.
            startActivity(new Intent(Intent.ACTION_EDIT, uri).setClassName(/* TODO: provide the application ID. For example: */ getPackageName(), "com.example.android.notepad.NoteEditor"));
        }
    }
    
    /**
     * 显示分类选择对话框
     */
    private void showCategoryDialog() {
        // 获取所有分类
        java.util.Set<String> categorySet = getAllCategories();
        final String[] categories = categorySet.toArray(new String[0]);
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("选择分类")
                .setItems(categories, (dialog, which) -> {
                    String selectedCategory = categories[which];
                    filterByCategory(selectedCategory);
                })
                .setNeutralButton("新增分类", (dialog, which) -> {
                    showAddCategoryDialog();
                })
                .setPositiveButton("管理分类", (dialog, which) -> {
                    showManageCategoriesDialog();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    /**
     * 显示添加分类对话框
     */
    private void showAddCategoryDialog() {
        // 先获取所有已存在的分类
        final java.util.Set<String> existingCategories = getAllCategories();
        
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("请输入分类名称");
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("新增分类")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String categoryName = input.getText().toString().trim();
                    if (!categoryName.isEmpty()) {
                        // 调试信息
                        Log.d(TAG, "输入的分类名: " + categoryName);
                        Log.d(TAG, "已存在的分类: " + existingCategories.toString());
                        Log.d(TAG, "是否包含: " + existingCategories.contains(categoryName));
                        
                        // 检查分类名是否已存在
                        if (existingCategories.contains(categoryName)) {
                            // 分类已存在，直接筛选
                            Toast.makeText(this, "分类 '" + categoryName + "' 已存在", Toast.LENGTH_SHORT).show();
                            filterByCategory(categoryName);
                        } else {
                            // 新分类，直接创建笔记
                            Intent intent = new Intent(Intent.ACTION_INSERT, getIntent().getData());
                            intent.setClassName(getPackageName(), "com.example.android.notepad.NoteEditor");
                            intent.putExtra("category", categoryName);
                            Toast.makeText(this, "正在创建分类 '" + categoryName + "'", Toast.LENGTH_SHORT).show();
                            startActivity(intent);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    /**
     * 获取所有已存在的分类
     */
    private java.util.Set<String> getAllCategories() {
        java.util.Set<String> categorySet = new java.util.HashSet<>();
        
        Cursor cursor = getContentResolver().query(
                getIntent().getData(),
                new String[]{NotePad.Notes.COLUMN_NAME_CATEGORY},
                null,
                null,
                null
        );
        
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String category = cursor.getString(0);
                if (category != null && !category.isEmpty()) {
                    categorySet.add(category);
                }
            }
            cursor.close();
        }
        
        // 如果没有任何分类，添加默认的"未分类"
        if (categorySet.isEmpty()) {
            categorySet.add("未分类");
        }
        
        return categorySet;
    }
    
    /**
     * 显示分类创建成功对话框
     */
    private void showCategoryCreatedDialog(final String categoryName) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("创建分类")
                .setMessage("分类 '" + categoryName + "' 已创建！\n\n" +
                        "该分类下暂无笔记。\n" +
                        "您可以：\n" +
                        "1. 新建笔记并设置为此分类\n" +
                        "2. 编辑现有笔记并设置分类")
                .setPositiveButton("新建笔记", (dialog, w) -> {
                    // 创建新笔记并设置分类
                    Intent intent = new Intent(Intent.ACTION_INSERT, getIntent().getData());
                    intent.setClassName(getPackageName(), "com.example.android.notepad.NoteEditor");
                    intent.putExtra("category", categoryName);
                    startActivity(intent);
                })
                .setNeutralButton("查看分类", (dialog, w) -> {
                    filterByCategory(categoryName);
                })
                .setNegativeButton("返回", null)
                .show();
    }
    
    /**
     * 按分类过滤笔记
     */
    private void filterByCategory(String category) {
        mCurrentCategory = category;
        
        String selection = null;
        String[] args = null;
        
        if (category != null && !"未分类".equals(category)) {
            selection = NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?";
            args = new String[]{category};
        }
        
        Cursor c = getContentResolver().query(
                Objects.requireNonNull(getIntent().getData()),
                PROJECTION,
                selection,
                args,
                NotePad.Notes.DEFAULT_SORT_ORDER);
                
        SimpleCursorAdapter a = (SimpleCursorAdapter) getListAdapter();
        a.changeCursor(c);
        
        // 确保新 Cursor 也能接收到数据变化通知
        c.setNotificationUri(getContentResolver(), getIntent().getData());
        
        // 更新标题显示当前分类
        if (category != null) {
            setTitle("分类: " + category);
        } else {
            setTitle(getText(R.string.title_notes_list));
        }
    }
    
    /**
     * 显示管理分类对话框
     */
    private void showManageCategoriesDialog() {
        java.util.Set<String> categorySet = getAllCategories();
        final String[] categories = categorySet.toArray(new String[0]);
        
        if (categories.length == 0) {
            Toast.makeText(this, "没有可管理的分类", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("管理分类 - 点击删除")
                .setItems(categories, (dialog, which) -> {
                    String selectedCategory = categories[which];
                    confirmDeleteCategory(selectedCategory);
                })
                .setNegativeButton("返回", null)
                .show();
    }
    
    /**
     * 确认删除分类
     */
    private void confirmDeleteCategory(final String categoryName) {
        // 统计该分类下的笔记数量
        Cursor cursor = getContentResolver().query(
                getIntent().getData(),
                new String[]{NotePad.Notes._ID},
                NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?",
                new String[]{categoryName},
                null
        );
        
        int noteCount = 0;
        if (cursor != null) {
            noteCount = cursor.getCount();
            cursor.close();
        }
        
        String message;
        if (noteCount > 0) {
            message = "确定要删除分类 '" + categoryName + "' 吗？\n\n" +
                    "该分类下有 " + noteCount + " 篇笔记。\n" +
                    "删除后，这些笔记将被设置为'未分类'。";
        } else {
            message = "确定要删除分类 '" + categoryName + "' 吗？";
        }
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("删除分类")
                .setMessage(message)
                .setPositiveButton("删除", (dialog, which) -> {
                    deleteCategory(categoryName);
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    /**
     * 删除分类（将该分类下的笔记设置为"未分类"）
     */
    private void deleteCategory(String categoryName) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, "未分类");
        
        int updatedCount = getContentResolver().update(
                getIntent().getData(),
                values,
                NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?",
                new String[]{categoryName}
        );
        
        if (updatedCount > 0) {
            Toast.makeText(this, "已删除分类 '" + categoryName + "'，" + updatedCount + " 篇笔记已设置为'未分类'", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "分类 '" + categoryName + "' 已删除", Toast.LENGTH_SHORT).show();
        }
        
        // 刷新列表
        if (categoryName.equals(mCurrentCategory)) {
            // 如果当前正在查看被删除的分类，则显示全部笔记
            mCurrentCategory = null;
            doSearch("");
        } else {
            // 刷新当前视图
            Cursor c = getContentResolver().query(
                    Objects.requireNonNull(getIntent().getData()),
                    PROJECTION,
                    null,
                    null,
                    NotePad.Notes.DEFAULT_SORT_ORDER);
            SimpleCursorAdapter a = (SimpleCursorAdapter) getListAdapter();
            a.changeCursor(c);
            c.setNotificationUri(getContentResolver(), getIntent().getData());
        }
    }
}

