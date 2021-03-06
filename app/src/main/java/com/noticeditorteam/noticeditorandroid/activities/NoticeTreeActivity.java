package com.noticeditorteam.noticeditorandroid.activities;

import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.nononsenseapps.filepicker.FilePickerActivity;
import com.noticeditorteam.noticeditorandroid.R;
import com.noticeditorteam.noticeditorandroid.fragments.FileTypeFragment;
import com.noticeditorteam.noticeditorandroid.fragments.RenameDialogFragment;
import com.noticeditorteam.noticeditorandroid.io.DocumentFormat;
import com.noticeditorteam.noticeditorandroid.io.exportstrategies.ExportStrategy;
import com.noticeditorteam.noticeditorandroid.io.exportstrategies.ExportStrategyHolder;
import com.noticeditorteam.noticeditorandroid.model.NoticeItem;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;

public class NoticeTreeActivity extends AppCompatActivity implements
        RenameDialogFragment.RenameDialogListener, FileTypeFragment.OnFragmentInteractionListener {

    private static final int EDIT_NOTICE_REQUEST = 1;
    private static final int SELECT_FILE_REQUEST = 2;

    private static final String ARG_TREE = "tree";
    private static final String ARG_FILE = "file";
    private static final String ARG_NAME = "name";
    private static final String ARG_POSITION = "position";

    private static final String SAVE_TREE = "tree";
    private static final String SAVE_FILE = "file";

    private static final String RESULT_TREE = "tree";

    private NoticeItem current;
    private ArrayAdapter<NoticeItem> adapter;
    private ArrayDeque<NoticeItem> pathlist = new ArrayDeque<>();
    private String path, savepath;
    private ExportStrategy currentExportStrategy = ExportStrategyHolder.ZIP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice_tree);
        current = getIntent().getParcelableExtra(ARG_TREE);
        path = getIntent().getStringExtra(ARG_FILE);
        if(savedInstanceState != null) {
            current = savedInstanceState.getParcelable(SAVE_TREE);
            path = savedInstanceState.getString(SAVE_FILE);
        }
        if(pathlist.isEmpty()) pathlist.addLast(current);
        ListView list = (ListView) findViewById(R.id.noticeview);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>(current.getChildren()));
        list.setAdapter(adapter);
        list.setLongClickable(true);
        list.setOnItemClickListener((AdapterView<?> parent, View itemClicked, int position, long id) -> {
            current = adapter.getItem(position);
            assert current != null;
            if(current.isBranch()) {
                pathlist.addLast(current);
                adapter.clear();
                adapter.addAll(new ArrayList<>(current.getChildren()));
                adapter.notifyDataSetChanged();
            }
            else {
                Intent intent = new Intent(this, NoticeWorkActivity.class);
                intent.putExtra(ARG_TREE, current);
                startActivityForResult(intent, 1);
            }
        });
        registerForContextMenu(list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_tree, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch(id) {
            case R.id.newbranchitem:
                NoticeItem newBranch = new NoticeItem("New branch");
                current.getChildren().add(newBranch);
                adapter.add(newBranch);
                adapter.notifyDataSetChanged();
                break;
            case R.id.newnoticeitem:
                NoticeItem newNotice = new NoticeItem("New notice", "Enter your notice here");
                current.getChildren().add(newNotice);
                adapter.add(newNotice);
                adapter.notifyDataSetChanged();
                break;
            case R.id.saveitem:
                if(path != null) {
                    NoticeItem root = pathlist.getFirst();
                    DocumentFormat.save(root, new File(path), currentExportStrategy);
                }
                else {
                    showSaveDialog();
                    path = savepath;
                }
                break;
            case R.id.saveasitem:
                showSaveDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if(v.getId() == R.id.noticeview)
            getMenuInflater().inflate(R.menu.context_menu_bnotice, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        NoticeItem changingItem = adapter.getItem(info.position);
        switch (id) {
            case R.id.renameitem:
                DialogFragment fragment = new RenameDialogFragment();
                Bundle args = new Bundle();
                args.putInt(ARG_POSITION, info.position);
                assert changingItem != null;
                args.putString(ARG_NAME, changingItem.getTitle());
                fragment.setArguments(args);
                fragment.show(getFragmentManager(), "missiles");
                break;
            case R.id.deletebnoticeitem:
                current.getChildren().remove(changingItem);
                adapter.remove(changingItem);
                adapter.notifyDataSetChanged();
                break;
        }
        return super.onContextItemSelected(item);
    }

    private void showSaveDialog() {
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
        intent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
        intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_NEW_FILE);
        intent.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());
        startActivityForResult(intent, SELECT_FILE_REQUEST);
    }

    @Override
    public void onBackPressed() {
        if(pathlist.size() > 1) {
            NoticeItem last = pathlist.peekLast();
            if(last.equals(current)) pathlist.removeLast();
            current = pathlist.getLast();
            adapter.clear();
            adapter.addAll(new ArrayList<>(current.getChildren()));
            adapter.notifyDataSetChanged();
        }
        else finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case EDIT_NOTICE_REQUEST:
                NoticeItem oldcurrent = current;
                current = pathlist.getLast();
                NoticeItem notice = data.getParcelableExtra(RESULT_TREE);
                int ind = current.getChildren().indexOf(oldcurrent);
                current.getChildren().set(ind, notice);
                adapter.clear();
                adapter.addAll(new ArrayList<>(current.getChildren()));
                adapter.notifyDataSetChanged();
                break;
            case SELECT_FILE_REQUEST:
                savepath = data.getData().getPath();
                DialogFragment fragment = FileTypeFragment.newInstance(savepath);
                fragment.show(getFragmentManager(), "missiles");
                break;
        }
    }

    @Override
    public void onDialogPositiveClick(RenameDialogFragment dialog) {
        Bundle args = dialog.getArguments();
        NoticeItem renamingItem = adapter.getItem(args.getInt(ARG_POSITION));
        int ind = current.getChildren().indexOf(renamingItem);
        assert renamingItem != null;
        renamingItem.setTitle(dialog.getNoticeName().getText().toString());
        current.getChildren().set(ind, renamingItem);
        adapter.clear();
        adapter.addAll(new ArrayList<>(current.getChildren()));
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SAVE_TREE, current);
        outState.putString(SAVE_FILE, path);
    }

    @Override
    public void onFragmentInteraction(String path, ExportStrategy strategy) {
        try {
            savepath = path;
            NoticeItem root = pathlist.getFirst();
            DocumentFormat.save(root, new File(savepath), strategy);
            currentExportStrategy = strategy;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
