package net.bither.ui.base.passwordkeyboard;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import net.bither.R;
import net.bither.util.LogUtil;
import net.bither.util.StringUtil;
import net.bither.util.ThreadUtil;

import java.lang.reflect.Method;

/**
 * Created by songchenwen on 14-7-21.
 */
public class PasswordEntryKeyboardView extends KeyboardView implements KeyboardView
        .OnKeyboardActionListener, View.OnFocusChangeListener, View.OnClickListener,
        View.OnTouchListener {
    private static final String TAG = "PasswordEntryKeyboardHelper";

    public static final int KEYBOARD_MODE_ALPHA = 0;
    public static final int KEYBOARD_MODE_NUMERIC = 1;
    private static final int KEYBOARD_STATE_NORMAL = 0;
    private static final int KEYBOARD_STATE_SHIFTED = 1;
    private static final int KEYBOARD_STATE_CAPSLOCK = 2;

    private int mKeyboardMode = KEYBOARD_MODE_ALPHA;
    private int mKeyboardState = KEYBOARD_STATE_NORMAL;

    private PasswordEntryKeyboard mQwertyKeyboard;
    private PasswordEntryKeyboard mQwertyKeyboardShifted;
    private PasswordEntryKeyboard mNumericKeyboard;

    private InputMethodManager imm;

    private Object viewRootImpl;

    public PasswordEntryKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PasswordEntryKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init(){
        imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        setOnKeyboardActionListener(PasswordEntryKeyboardView.this);
        getViewRootImpl();
        createKeyboards();
        setKeyboardMode(KEYBOARD_MODE_NUMERIC);
    }

    public void createKeyboards() {
        createKeyboardsWithDefaultWidth();
    }

    public boolean isAlpha() {
        return mKeyboardMode == KEYBOARD_MODE_ALPHA;
    }

    private void createKeyboardsWithDefaultWidth() {
        mNumericKeyboard = new PasswordEntryKeyboard(getContext(), R.xml.password_keyboard_number);
        mQwertyKeyboard = new PasswordEntryKeyboard(getContext(), R.xml.password_keyboard_letter,
                0);
        mQwertyKeyboard.enableShiftLock();

        mQwertyKeyboardShifted = new PasswordEntryKeyboard(getContext(),
                R.xml.password_keyboard_letter, 0);
        mQwertyKeyboardShifted.enableShiftLock();
        mQwertyKeyboardShifted.setShifted(true); // always shifted.
    }

    public void setKeyboardMode(int mode) {
        switch (mode) {
            case KEYBOARD_MODE_ALPHA:
                setKeyboard(mQwertyKeyboard);
                mKeyboardState = KEYBOARD_STATE_NORMAL;
                setPreviewEnabled(false);
                break;
            case KEYBOARD_MODE_NUMERIC:
                setKeyboard(mNumericKeyboard);
                mKeyboardState = KEYBOARD_STATE_NORMAL;
                setPreviewEnabled(false); // never show popup for numeric keypad
                break;
        }
        mKeyboardMode = mode;
    }

    private void sendKeyEventsToTarget(int character) {
        if (viewRootImpl == null) {
            getViewRootImpl();
        }
        KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(new
                char[]{(char) character});
        try {
            Method method = viewRootImpl.getClass().getDeclaredMethod("dispatchKeyFromIme",
                    KeyEvent.class);
            method.setAccessible(true);
            if (events != null) {
                final int N = events.length;
                for (int i = 0;
                     i < N;
                     i++) {
                    KeyEvent event = events[i];
                    event = KeyEvent.changeFlags(event, event.getFlags() | KeyEvent
                            .FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
                    method.invoke(viewRootImpl, event);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogUtil.e(TAG, "can not dispatch input event");
        }
    }

    public void sendDownUpKeyEvents(int keyEventCode) {
        if (viewRootImpl == null) {
            getViewRootImpl();
        }
        long eventTime = SystemClock.uptimeMillis();
        try {
            Method method = viewRootImpl.getClass().getDeclaredMethod("dispatchKeyFromIme",
                    KeyEvent.class);
            method.setAccessible(true);
            method.invoke(viewRootImpl, new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN,
                    keyEventCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
            method.invoke(viewRootImpl, new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP,
                    keyEventCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        } catch (Exception e) {
            e.printStackTrace();
            LogUtil.e(TAG, "can not dispatch key from ime");
        }
    }

    public void onKey(int primaryCode, int[] keyCodes) {
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            handleModeChange();
        } else if (primaryCode == PasswordEntryKeyboard.KEYCODE_ENTER) {
            handleEnter();
        } else {
            handleCharacter(primaryCode, keyCodes);
            // Switch back to old keyboard if we're not in capslock mode
            if (mKeyboardState == KEYBOARD_STATE_SHIFTED) {
                // skip to the unlocked state
                mKeyboardState = KEYBOARD_STATE_CAPSLOCK;
                handleShift();
            }
        }
    }

    private void handleEnter() {
        View currentFocusView = getRootView().findFocus();
        if (currentFocusView == null) {
            return;
        }
        if (currentFocusView instanceof EditText) {
            EditText currentFocusEt = (EditText) currentFocusView;
            if (currentFocusEt.getImeActionId() > 0) {
                currentFocusEt.onEditorAction(currentFocusEt.getImeActionId());
            } else {
                View nextFocusView = currentFocusEt.focusSearch(View.FOCUS_DOWN);
                if (nextFocusView != null) {
                    nextFocusView.requestFocus(View.FOCUS_DOWN);
                    return;
                } else {
                    if (imm.isActive(currentFocusEt)) {
                        imm.hideSoftInputFromWindow(currentFocusEt.getWindowToken(), 0);
                    }
                    hideKeyboard();
                    return;
                }
            }
        }
    }

    private void handleModeChange() {
        final Keyboard current = getKeyboard();
        Keyboard next = null;
        if (current == mQwertyKeyboard || current == mQwertyKeyboardShifted) {
            next = mNumericKeyboard;
            mKeyboardMode = KEYBOARD_MODE_NUMERIC;
        } else if (current == mNumericKeyboard) {
            next = mQwertyKeyboard;
            mKeyboardMode = KEYBOARD_MODE_ALPHA;
        }
        if (next != null) {
            setKeyboard(next);
            mKeyboardState = KEYBOARD_STATE_NORMAL;
        }
    }

    public void handleBackspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    private void handleShift() {
        Keyboard current = getKeyboard();
        PasswordEntryKeyboard next = null;
        final boolean isAlphaMode = current == mQwertyKeyboard || current == mQwertyKeyboardShifted;
        if (mKeyboardState == KEYBOARD_STATE_NORMAL) {
            mKeyboardState = isAlphaMode ? KEYBOARD_STATE_SHIFTED : KEYBOARD_STATE_CAPSLOCK;
            next = mQwertyKeyboardShifted;
        } else if (mKeyboardState == KEYBOARD_STATE_SHIFTED) {
            mKeyboardState = KEYBOARD_STATE_CAPSLOCK;
            next = mQwertyKeyboardShifted;
        } else if (mKeyboardState == KEYBOARD_STATE_CAPSLOCK) {
            mKeyboardState = KEYBOARD_STATE_NORMAL;
            next = mQwertyKeyboard;
        }
        if (next != null) {
            if (next != current) {
                setKeyboard(next);
            }
            next.setShiftLocked(mKeyboardState == KEYBOARD_STATE_CAPSLOCK);
            setShifted(mKeyboardState != KEYBOARD_STATE_NORMAL);
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        // Maybe turn off shift if not in capslock mode.
        if (isShifted() && primaryCode != ' ' && primaryCode != '\n') {
            primaryCode = Character.toUpperCase(primaryCode);
        }
        sendKeyEventsToTarget(primaryCode);
    }

    private void handleClose() {
        hideKeyboard();
    }

    private void getViewRootImpl() {
        try {
            Method method = View.class.getDeclaredMethod("getViewRootImpl");
            method.setAccessible(true);
            viewRootImpl = method.invoke(this);
        } catch (Exception e) {
            e.printStackTrace();
            LogUtil.e(TAG, "can not get view root imp");
        }
    }

    public int getKeyboardHeight() {
        Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return 0;
        }
        return keyboard.getHeight();
    }

    @Override
    public void onPress(int primaryCode) {

    }

    public void onRelease(int primaryCode) {

    }

    public void onText(CharSequence text) {

    }

    public void swipeDown() {

    }

    public void swipeLeft() {

    }

    public void swipeRight() {

    }

    public void swipeUp() {

    }

    public boolean isKeyboardShown() {
        return getVisibility() == View.VISIBLE;
    }

    public void showKeyboard() {
        if (getKeyboard() == mQwertyKeyboardShifted) {
            setKeyboard(mQwertyKeyboard);
            mKeyboardState = KEYBOARD_STATE_NORMAL;
        }
        removeCallbacks(showKeyboardRunnable);
        configureDoneButton();
        if (isKeyboardShown()) {
            return;
        }
        ThreadUtil.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                boolean result = imm.hideSoftInputFromWindow(getWindowToken(), 0,
                        new ResultReceiver(null) {
                            @Override
                            protected void onReceiveResult(int resultCode, Bundle resultData) {
                                if (resultCode == InputMethodManager.RESULT_HIDDEN || resultCode
                                        == InputMethodManager.RESULT_UNCHANGED_HIDDEN) {
                                    postDelayed(showKeyboardRunnable, 100);
                                }
                                super.onReceiveResult(resultCode, resultData);
                            }
                        }
                );
                if (!result) {
                    setVisibility(View.VISIBLE);
                    setEnabled(true);
                }
            }
        });
    }

    private Runnable showKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            setVisibility(View.VISIBLE);
            setEnabled(true);
        }
    };

    public void hideKeyboard() {
        removeCallbacks(showKeyboardRunnable);
        if (!isKeyboardShown()) {
            return;
        }
        ThreadUtil.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                imm.hideSoftInputFromWindow(getWindowToken(), 0, null);
                setVisibility(View.GONE);
                setEnabled(false);
            }
        });
    }

    public PasswordEntryKeyboardView registerEditText(EditText... ets) {
        for (EditText et : ets) {
            et.setOnFocusChangeListener(this);
            et.setOnClickListener(this);
            et.setOnTouchListener(this);
        }
        return this;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            showKeyboard();
        } else {
            hideKeyboard();
        }
    }

    @Override
    public void onClick(View v) {
        showKeyboard();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v instanceof EditText) {
            EditText edittext = (EditText) v;
            int inType = edittext.getInputType();       // Backup the input type
            edittext.setInputType(InputType.TYPE_NULL); // Disable standard keyboard
            edittext.onTouchEvent(event);               // Call native handler
            edittext.setInputType(inType);              // Restore input type
            edittext.setSelection(edittext.getText().length());
            return true;
        }
        return false;
    }

    private void configureDoneButton() {
        View currentFocusView = getRootView().findFocus();
        if (currentFocusView == null) {
            return;
        }
        if (currentFocusView instanceof EditText) {
            EditText currentFocusEt = (EditText) currentFocusView;
            if (!StringUtil.isEmpty(currentFocusEt.getImeActionLabel() == null ? null :
                    currentFocusEt.getImeActionLabel().toString())) {
                mNumericKeyboard.setEnterKeyText(currentFocusEt.getImeActionLabel());
                mQwertyKeyboard.setEnterKeyText(currentFocusEt.getImeActionLabel());
                mQwertyKeyboardShifted.setEnterKeyText(currentFocusEt.getImeActionLabel());
                return;
            }
            if (currentFocusEt.getImeActionId() > 0) {
                switch (currentFocusEt.getImeActionId()) {
                    case EditorInfo.IME_ACTION_DONE:
                    case EditorInfo.IME_ACTION_GO:
                    case EditorInfo.IME_ACTION_SEND:
                        mNumericKeyboard.setEnterKeyResources(getContext().getResources(), 0, 0,
                                R.string.password_keyboard_done);
                        mQwertyKeyboard.setEnterKeyResources(getContext().getResources(), 0, 0,
                                R.string.password_keyboard_done);
                        mQwertyKeyboardShifted.setEnterKeyResources(getContext().getResources(),
                                0, 0, R.string.password_keyboard_done);
                        return;
                    case EditorInfo.IME_ACTION_NEXT:
                        mNumericKeyboard.setEnterKeyResources(getContext().getResources(), 0, 0,
                                R.string.password_keyboard_next);
                        mQwertyKeyboard.setEnterKeyResources(getContext().getResources(), 0, 0,
                                R.string.password_keyboard_next);
                        mQwertyKeyboardShifted.setEnterKeyResources(getContext().getResources(),
                                0, 0, R.string.password_keyboard_next);
                        return;
                    default:
                        break;
                }
            }
            View nextFocusView = currentFocusEt.focusSearch(View.FOCUS_DOWN);
            if (nextFocusView != null) {
                mNumericKeyboard.setEnterKeyResources(getContext().getResources(), 0, 0,
                        R.string.password_keyboard_next);
                mQwertyKeyboard.setEnterKeyResources(getContext().getResources(), 0, 0,
                        R.string.password_keyboard_next);
                mQwertyKeyboardShifted.setEnterKeyResources(getContext().getResources(), 0, 0,
                        R.string.password_keyboard_next);
            } else {
                mNumericKeyboard.setEnterKeyResources(getContext().getResources(), 0, 0,
                        R.string.password_keyboard_done);
                mQwertyKeyboard.setEnterKeyResources(getContext().getResources(), 0, 0,
                        R.string.password_keyboard_done);
                mQwertyKeyboardShifted.setEnterKeyResources(getContext().getResources(), 0, 0,
                        R.string.password_keyboard_done);
            }
            PasswordEntryKeyboard keyboard = (PasswordEntryKeyboard) getKeyboard();
            invalidateKey(keyboard.getEnterKeyIndex());
        }
    }
}
