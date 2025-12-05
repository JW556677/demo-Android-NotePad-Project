# NotePad - Android 笔记应用

一个功能丰富的 Android 笔记应用，具有时间戳显示、搜索查询、颜色标记、自动保存和分类管理等功能。

## 项目概述

本项目基于 Android NotePad 示例应用进行扩展开发，添加了多项实用功能，提升用户体验。

## 基本功能

1.新建笔记和删除笔记

<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/59de8826-ce67-48ba-a7f9-a5ff2aa1bdfb" />

点击右上角的新建按钮可以新建笔记

<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/ac4b982c-0660-4e42-ad70-e172252dbc3d" />

新建笔记界面可以自行编辑标题和内容

<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/7884f00e-46e9-413e-9c9a-20e96ee4a483" />

点击右上角的“保存”和“删除”按钮可以对笔记进行保存和删除

<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/3a64d947-230c-4236-8b36-61716680b9ca" />

点击右上角的更多功能可以编辑笔记标题

## 核心功能

### 1. 时间戳显示

在笔记列表中显示每条笔记的最后修改时间，方便用户快速了解笔记的更新情况。
效果如图：

<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/c25dbca2-a630-4ead-8738-4d58397299c8" />


#### 功能特点
- 格式化显示时间：`yyyy-MM-dd HH:mm`
- 自动更新修改时间
- 在列表项底部以灰色小字显示

#### 实现代码

**布局文件** (`noteslist_item.xml`)：
```xml
<TextView
    android:id="@+id/time_stamp"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:textSize="12sp"
    android:textColor="#999999"
    android:layout_marginTop="4dp"
    android:text="时间戳" />
```

**适配器绑定** (`NotesList.java`)：
```java
// ViewBinder 处理时间戳格式化
adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
    @Override
    public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
        if (view.getId() == R.id.time_stamp) {
            // 获取时间戳并格式化
            long t = cursor.getLong(columnIndex);
            String txt = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd  HH:mm",
                    java.util.Locale.CHINA).format(new java.util.Date(t));
            ((TextView) view).setText(txt);
            return true;
        }
        return false;
    }
});
```

**时间戳更新** (`NoteEditor.java`)：
```java
private final void updateNote(String text, String title) {
    ContentValues values = new ContentValues();
    values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
    values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
    values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
    
    getContentResolver().update(mUri, values, null, null);
}
```

---

### 2. 笔记查询

提供强大的搜索功能，支持按标题和内容进行模糊查询。

#### 功能特点
- 实时搜索笔记标题和内容
- 支持模糊匹配
- 搜索结果即时显示
- 搜索框采用 SearchView 组件

#### 实现效果

点击右上角的搜索按钮可按标题和内容进行模糊查询

<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/78c4f2ab-539d-41ab-abd0-5a7416b9e553" />
<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/b871bd14-b1c4-4283-89da-c06e5ef16ff9" />
<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/d3e1c7db-31b6-4833-b9e4-2aa3351f02c1" />


#### 实现代码

**菜单添加搜索按钮** (`NotesList.java`)：
```java
@Override
public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.list_options_menu, menu);
    
    // 添加搜索按钮
    menu.add(0, Menu.FIRST + 1, 0, "搜索")
            .setIcon(android.R.drawable.ic_menu_search)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    
    return super.onCreateOptionsMenu(menu);
}
```

**搜索对话框** (`NotesList.java`)：
```java
@Override
public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    
    if (id == Menu.FIRST + 1) {  // 搜索
        android.widget.SearchView sv = new android.widget.SearchView(this);
        sv.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
            @Override 
            public boolean onQueryTextSubmit(String s) {
                doSearch(s);
                return true;
            }
            @Override 
            public boolean onQueryTextChange(String s) {
                return true;
            }
        });
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("输入关键词")
                .setView(sv)
                .setPositiveButton("确定", (dialog, which) -> {
                    CharSequence query = sv.getQuery();
                    doSearch(query != null ? query.toString() : "");
                })
                .setNegativeButton("取消", null)
                .show();
        return true;
    }
    return super.onOptionsItemSelected(item);
}
```

**搜索实现** (`NotesList.java`)：
```java
private void doSearch(String key) {
    // 构建查询条件：标题或内容包含关键词
    String selection = TextUtils.isEmpty(key) ? null :
            NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR " +
            NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?";
    String[] args = TextUtils.isEmpty(key) ? null :
            new String[]{"%" + key + "%", "%" + key + "%"};
    
    // 执行查询
    Cursor c = getContentResolver().query(
            Objects.requireNonNull(getIntent().getData()),
            PROJECTION,
            selection,
            args,
            NotePad.Notes.DEFAULT_SORT_ORDER);
    
    // 更新适配器
    SimpleCursorAdapter a = (SimpleCursorAdapter) getListAdapter();
    a.changeCursor(c);
    c.setNotificationUri(getContentResolver(), getIntent().getData());
}
```

---

### 3. 笔记颜色选择

为笔记添加颜色标记，通过不同颜色区分笔记类别或重要程度。

#### 功能特点
- 提供 7 种预设颜色
- 颜色以卡片背景形式显示
- 编辑笔记时可随时更改颜色
- 默认白色背景

#### 实现效果
在笔记详情页右上角的更多选项中选择颜色
<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/3369523e-6889-4533-a4dc-601613415bf4" />
可选择七种不同的颜色
<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/94d4eca3-ee4d-420b-9d41-1540b53e7389" />
选择后返回列表页，笔记将会改变颜色
<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/bc94ecc4-9089-45e1-9bc2-3c61a9c58202" />



#### 实现代码

**颜色定义** (`colors.xml`)：
```xml
<resources>
    <!-- 笔记颜色 -->
    <color name="note_green">#A8E6CF</color>
    <color name="note_orange">#FFD3B6</color>
    <color name="note_yellow">#FFAAA5</color>
    <color name="note_pink">#FF8B94</color>
    <color name="note_blue">#A2D2FF</color>
    <color name="note_purple">#D4A5FF</color>
    <color name="note_gray">#E0E0E0</color>
</resources>
```

**数据库颜色列** (`NotePadProvider.java`)：
```java
@Override
public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
            + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
            + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
            + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
            + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
            + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
            + NotePad.Notes.COLUMN_NAME_COLOR + " INTEGER DEFAULT 0,"  // 颜色列
            + NotePad.Notes.COLUMN_NAME_CATEGORY + " TEXT DEFAULT '未分类'"
            + ");");
}
```

**颜色选择菜单** (`NoteEditor.java`)：
```java
@Override
public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.editor_options_menu, menu);
    
    // 添加颜色选择菜单
    menu.add(0, Menu.FIRST + 10, 0, "选择颜色");
    
    return super.onCreateOptionsMenu(menu);
}
```

**颜色选择对话框** (`NoteEditor.java`)：
```java
@Override
public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == Menu.FIRST + 10) {  // 选择颜色
        final int[] colors = {
                0xFFA8E6CF,  // 绿色
                0xFFFFD3B6,  // 橙色
                0xFFFFAAA5,  // 黄色
                0xFFFF8B94,  // 粉色
                0xFFA2D2FF,  // 蓝色
                0xFFD4A5FF,  // 紫色
                0xFFE0E0E0   // 灰色
        };
        
        final String[] colorNames = {
                "绿色", "橙色", "黄色", "粉色", "蓝色", "紫色", "灰色"
        };
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("选择笔记颜色")
                .setItems(colorNames, (dialog, which) -> {
                    updateNoteColor(colors[which]);
                })
                .setNegativeButton("取消", null)
                .show();
        return true;
    }
    return super.onOptionsItemSelected(item);
}
```

**更新颜色** (`NoteEditor.java`)：
```java
private void updateNoteColor(int color) {
    ContentValues values = new ContentValues();
    values.put(NotePad.Notes.COLUMN_NAME_COLOR, color);
    getContentResolver().update(mUri, values, null, null);
    getContentResolver().notifyChange(mUri, null);
}
```

**显示颜色** (`NotesList.java`)：
```java
adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
    @Override
    public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
        if (view.getId() == android.R.id.text1) {
            // 设置标题
            String title = cursor.getString(columnIndex);
            ((TextView) view).setText(title);
            
            // 处理颜色 - 给 CardView 设置背景色
            int colorColIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_COLOR);
            if (colorColIndex != -1) {
                int color = cursor.getInt(colorColIndex);
                View parent = (View) view.getParent();
                if (parent != null && parent.getParent() instanceof androidx.cardview.widget.CardView) {
                    androidx.cardview.widget.CardView cardView = 
                            (androidx.cardview.widget.CardView) parent.getParent();
                    if (color == 0) {
                        cardView.setCardBackgroundColor(0xFFFFFFFF);  // 默认白色
                    } else {
                        cardView.setCardBackgroundColor(color);
                    }
                }
            }
            return true;
        }
        return false;
    }
});
```

---

### 4. 保存笔记

自动保存机制，无需手动保存，支持标题和内容分别编辑。

#### 功能特点
- 离开编辑器时自动保存
- 支持独立的标题输入框
- 空笔记自动删除
- 防止崩溃的异常处理

#### 实现效果
点击右上角的导出按钮可以导出为.txt格式的文件
<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/4b9b632a-733a-4058-9522-abf6137a9cbc" />
<img width="569" height="1010" alt="image" src="https://github.com/user-attachments/assets/1c31f025-0e20-4259-b23f-db08f5a549dc" />


#### 实现代码

**编辑器布局** (`note_editor.xml`)：
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- 标题输入框 -->
    <EditText
        android:id="@+id/note_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="标题"
        android:textSize="20sp"
        android:textStyle="bold"
        android:padding="16dp"
        android:background="@android:color/transparent" />

    <!-- 内容输入框 -->
    <com.example.android.notepad.NoteEditor$LinedEditText
        android:id="@+id/note"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:hint="笔记内容"
        android:padding="16dp"
        android:gravity="top"
        android:scrollbars="vertical" />
</LinearLayout>
```

**自动保存逻辑** (`NoteEditor.java`)：
```java
@Override
protected void onPause() {
    super.onPause();
    
    if (mCursor != null) {
        // 获取当前内容和标题
        String text = mText.getText().toString();
        int length = text.length();
        
        String title = "";
        if (mTitleText != null) {
            title = mTitleText.getText().toString().trim();
        }
        
        // 如果标题和内容都为空，删除笔记
        if (isFinishing() && (length == 0) && (title.isEmpty())) {
            setResult(RESULT_CANCELED);
            deleteNote();
        } else if (mState == STATE_EDIT) {
            // 编辑模式：保存更改
            try {
                updateNote(text, title);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (mState == STATE_INSERT) {
            // 插入模式：保存新笔记
            try {
                updateNote(text, title);
                mState = STATE_EDIT;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
```

**更新笔记** (`NoteEditor.java`)：
```java
private final void updateNote(String text, String title) {
    // 如果标题为空，使用内容的前几个字符作为标题
    if (title == null || title.isEmpty()) {
        int length = text.length();
        title = text.substring(0, Math.min(30, length));
        if (length > 30) {
            int lastSpace = title.lastIndexOf(' ');
            if (lastSpace > 0) {
                title = title.substring(0, lastSpace);
            }
        }
    }
    
    // 创建更新值
    ContentValues values = new ContentValues();
    values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
    values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
    values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
    
    // 更新数据库
    getContentResolver().update(mUri, values, null, null);
    getContentResolver().notifyChange(mUri, null);
}
```

**删除空笔记** (`NoteEditor.java`)：
```java
private final void deleteNote() {
    if (mCursor != null) {
        mCursor.close();
        mCursor = null;
        getContentResolver().delete(mUri, null, null);
        mText.setText("");
    }
}
```

---

### 5. 笔记分类

强大的分类管理功能，支持创建、选择、筛选和删除分类。

#### 功能特点
- 自定义分类名称
- 按分类筛选笔记
- 分类管理（删除分类）
- 新建笔记时指定分类
- 删除分类时自动处理笔记归属

#### 实现代码

**数据库分类列** (`NotePadProvider.java`)：
```java
@Override
public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
            + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
            + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
            + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
            + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
            + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
            + NotePad.Notes.COLUMN_NAME_COLOR + " INTEGER DEFAULT 0,"
            + NotePad.Notes.COLUMN_NAME_CATEGORY + " TEXT DEFAULT '未分类'"  // 分类列
            + ");");
}

@Override
public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < 4) {
        // 添加分类列
        try {
            db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME +
                    " ADD COLUMN category TEXT DEFAULT '未分类'");
        } catch (Exception e) {
            Log.w(TAG, "Category column may already exist", e);
        }
    }
}
```

**分类常量定义** (`NotePad.java`)：
```java
public static final class Notes implements BaseColumns {
    // 列名
    public static final String COLUMN_NAME_TITLE = "title";
    public static final String COLUMN_NAME_NOTE = "note";
    public static final String COLUMN_NAME_CREATE_DATE = "created";
    public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";
    public static final String COLUMN_NAME_COLOR = "color";
    public static final String COLUMN_NAME_CATEGORY = "category";  // 分类列
}
```

**分类菜单** (`NotesList.java`)：
```java
@Override
public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.list_options_menu, menu);
    
    // 分类按钮
    menu.add(0, Menu.FIRST + 4, 0, "分类")
            .setIcon(android.R.drawable.ic_menu_agenda)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    
    return super.onCreateOptionsMenu(menu);
}
```

**显示分类对话框** (`NotesList.java`)：
```java
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
```

**获取所有分类** (`NotesList.java`)：
```java
private java.util.Set<String> getAllCategories() {
    java.util.Set<String> categorySet = new java.util.HashSet<>();
    
    Cursor cursor = getContentResolver().query(
            getIntent().getData(),
            new String[]{NotePad.Notes.COLUMN_NAME_CATEGORY},
            null, null, null
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
```

**新增分类** (`NotesList.java`)：
```java
private void showAddCategoryDialog() {
    final java.util.Set<String> existingCategories = getAllCategories();
    
    final android.widget.EditText input = new android.widget.EditText(this);
    input.setHint("请输入分类名称");
    
    new android.app.AlertDialog.Builder(this)
            .setTitle("新增分类")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                String categoryName = input.getText().toString().trim();
                if (!categoryName.isEmpty()) {
                    if (existingCategories.contains(categoryName)) {
                        // 分类已存在
                        Toast.makeText(this, "分类 '" + categoryName + "' 已存在", 
                                Toast.LENGTH_SHORT).show();
                        filterByCategory(categoryName);
                    } else {
                        // 新分类，直接创建笔记
                        Intent intent = new Intent(Intent.ACTION_INSERT, getIntent().getData());
                        intent.setClassName(getPackageName(), 
                                "com.example.android.notepad.NoteEditor");
                        intent.putExtra("category", categoryName);
                        Toast.makeText(this, "正在创建分类 '" + categoryName + "'", 
                                Toast.LENGTH_SHORT).show();
                        startActivity(intent);
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
}
```

**按分类筛选** (`NotesList.java`)：
```java
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
    c.setNotificationUri(getContentResolver(), getIntent().getData());
    
    // 更新标题显示当前分类
    if (category != null) {
        setTitle("分类: " + category);
    } else {
        setTitle(getText(R.string.title_notes_list));
    }
}
```

**管理分类（删除）** (`NotesList.java`)：
```java
private void showManageCategoriesDialog() {
    java.util.Set<String> categorySet = getAllCategories();
    final String[] categories = categorySet.toArray(new String[0]);
    
    new android.app.AlertDialog.Builder(this)
            .setTitle("管理分类 - 点击删除")
            .setItems(categories, (dialog, which) -> {
                String selectedCategory = categories[which];
                confirmDeleteCategory(selectedCategory);
            })
            .setNegativeButton("返回", null)
            .show();
}

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
    
    String message = noteCount > 0 
            ? "确定要删除分类 '" + categoryName + "' 吗？\n\n" +
              "该分类下有 " + noteCount + " 篇笔记。\n" +
              "删除后，这些笔记将被设置为'未分类'。"
            : "确定要删除分类 '" + categoryName + "' 吗？";
    
    new android.app.AlertDialog.Builder(this)
            .setTitle("删除分类")
            .setMessage(message)
            .setPositiveButton("删除", (dialog, w) -> {
                deleteCategory(categoryName);
            })
            .setNegativeButton("取消", null)
            .show();
}

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
        Toast.makeText(this, "已删除分类 '" + categoryName + "'，" + 
                updatedCount + " 篇笔记已设置为'未分类'", Toast.LENGTH_LONG).show();
    }
    
    // 刷新列表
    if (categoryName.equals(mCurrentCategory)) {
        mCurrentCategory = null;
        doSearch("");
    }
}
```

**编辑器中设置分类** (`NoteEditor.java`)：
```java
@Override
protected void onResume() {
    super.onResume();
    
    if (mCursor != null) {
        // ... 其他代码 ...
        
        if (mState == STATE_INSERT) {
            setTitle(getText(R.string.title_create));
            
            // 检查是否从 Intent 中传入了分类
            String category = getIntent().getStringExtra("category");
            if (category != null && !category.isEmpty()) {
                mCurrentCategory = category;
                // 立即保存分类到数据库
                updateNoteCategory(category);
                Toast.makeText(this, "分类已设置为: " + category, 
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}

private void updateNoteCategory(String category) {
    if (mUri == null) return;
    
    ContentValues values = new ContentValues();
    values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, category);
    
    getContentResolver().update(mUri, values, null, null);
    getContentResolver().notifyChange(mUri, null);
}
```

---

## 技术特点

### UI 优化
- 使用 **CardView** 实现卡片式设计
- Material Design 配色方案
- 圆角卡片和阴影效果
- 清晰的视觉层次

### 数据持久化
- SQLite 数据库存储
- ContentProvider 数据访问
- 自动数据库版本升级
- 数据变化通知机制

### 性能优化
- Cursor 生命周期管理
- 数据变化监听 (`setNotificationUri`)
- 避免内存泄漏
- 异常处理保护

### 兼容性
- 支持 Android API 14+
- AndroidX 库支持
- 适配不同屏幕尺寸

---

## 项目结构

```
NotePad-main/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/android/notepad/
│   │   │   │   ├── NotesList.java          # 笔记列表
│   │   │   │   ├── NoteEditor.java         # 笔记编辑器
│   │   │   │   ├── NotePad.java            # 数据契约
│   │   │   │   └── NotePadProvider.java    # 内容提供者
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── noteslist_item.xml  # 列表项布局
│   │   │   │   │   └── note_editor.xml     # 编辑器布局
│   │   │   │   ├── values/
│   │   │   │   │   ├── colors.xml          # 颜色定义
│   │   │   │   │   └── styles.xml          # 样式定义
│   │   │   │   └── menu/
│   │   │   │       ├── list_options_menu.xml
│   │   │   │       └── editor_options_menu.xml
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── gradle.properties
└── README.md
```

---

## 编译和运行

### 环境要求
- Android Studio 4.0+
- Android SDK API 14+
- Gradle 8.0+

### 编译步骤
```bash
# 克隆项目
git clone <repository-url>

# 进入项目目录
cd NotePad-main

# 编译 Debug 版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

### 运行
1. 使用 Android Studio 打开项目
2. 连接 Android 设备或启动模拟器
3. 点击 Run 按钮

---

## 依赖库

```gradle
dependencies {
    implementation 'androidx.appcompat:appcompat:1.3.1'
    implementation 'androidx.cardview:cardview:1.0.0'
}
```

---

limitations under the License.
```



