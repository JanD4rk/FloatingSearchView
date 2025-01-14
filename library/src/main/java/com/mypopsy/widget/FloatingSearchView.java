package com.mypopsy.widget;

import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.BOTTOM;
import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.LEFT;
import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.RIGHT;
import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.TOP;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.MarginLayoutParamsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorCompat;

import com.mypopsy.floatingsearchview.R;
import com.mypopsy.widget.adapter.CustomRecyclerView;
import com.mypopsy.widget.internal.RoundRectDrawableWithShadow;
import com.mypopsy.widget.internal.SuggestionItemDecorator;
import com.mypopsy.widget.internal.ViewUtils;

import java.util.ArrayList;
import java.util.List;

public class FloatingSearchView extends RelativeLayout {

    private static final int DEFAULT_BACKGROUND_COLOR = 0x90000000;
    private static final int DEFAULT_CONTENT_COLOR = 0xfff0f0f0;

    private static final int DEFAULT_RADIUS = 16;
    private static final int DEFAULT_ELEVATION = 2;
    private static final int DEFAULT_MAX_ELEVATION = 2;

    private static final long DEFAULT_DURATION_ENTER = 300;
    private static final long DEFAULT_DURATION_EXIT = 400;

    private static final Interpolator DECELERATE = new DecelerateInterpolator(3f);
    private static final Interpolator ACCELERATE = new AccelerateInterpolator(2f);

    private final CustomRecyclerView.AdapterDataObserver mAdapterObserver = new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public void onChanged() {
            updateSuggestionsVisibility();
        }
    };

    public interface OnSearchListener {
        void onSearchAction(CharSequence text);
    }

    public interface OnIconClickListener {
        void onNavigationClick();
    }

    public interface OnSearchFocusChangedListener {
        void onFocusChanged(boolean focused);
    }

    final private EditText mSearchInput;
    final private CustomRecyclerView mRecyclerView;
    final private ViewGroup mSearchContainer;
    final private View mDivider;
    final private ProgressBar loading;
    final private ImageButton btnClear;
    final private Activity mActivity;

    final private RoundRectDrawableWithShadow mSearchBackground;
    final private SuggestionItemDecorator mCardDecorator;

    final private List<Integer> mAlwaysShowingMenu = new ArrayList<>();

    private OnSearchFocusChangedListener mFocusListener;
    private Drawable mBackgroundDrawable;
    private boolean mSuggestionsShown;

    public FloatingSearchView(Context context) {
        this(context, null);
    }

    public FloatingSearchView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.floatingSearchViewStyle);
    }

    public FloatingSearchView(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (isInEditMode()) {
            mActivity = null;
        } else {
            mActivity = getActivity();
        }

        setFocusable(true);
        setFocusableInTouchMode(true);

        inflate(getContext(), R.layout.fsv_floating_search_layout, this);

        mSearchInput = findViewById(R.id.fsv_search_text);
        mRecyclerView = findViewById(R.id.fsv_suggestions_list);
        mDivider = findViewById(R.id.fsv_suggestions_divider);
        mSearchContainer = findViewById(R.id.fsv_search_container);
        loading = findViewById(R.id.fsv_search_loading);
        btnClear = findViewById(R.id.fsv_search_clear_btn);


        //TODO: move elevation parameters to XML attributes
        mSearchBackground = new RoundRectDrawableWithShadow(
                DEFAULT_CONTENT_COLOR, ViewUtils.dpToPx(DEFAULT_RADIUS),
                ViewUtils.dpToPx(DEFAULT_ELEVATION),
                ViewUtils.dpToPx(DEFAULT_MAX_ELEVATION));
        mSearchBackground.setAddPaddingForCorners(true);

        mCardDecorator = new SuggestionItemDecorator(mSearchBackground.mutate());

        applyXmlAttributes(attrs, defStyleAttr, 0);
        setupViews();
    }

    private void applyXmlAttributes(AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                R.styleable.FloatingSearchView, defStyleAttr, defStyleRes);

        // Search bar width
        View suggestionsContainer = findViewById(R.id.fsv_suggestions_container);
        int searchBarWidth = a.getDimensionPixelSize(R.styleable.FloatingSearchView_fsv_searchBarWidth,
                mSearchContainer.getLayoutParams().width);
        mSearchContainer.getLayoutParams().width = searchBarWidth;
        suggestionsContainer.getLayoutParams().width = searchBarWidth;

        // Divider
        mDivider.setBackground(a.getDrawable(R.styleable.FloatingSearchView_android_divider));
        int dividerHeight = a.getDimensionPixelSize(R.styleable.FloatingSearchView_android_dividerHeight, -1);

        MarginLayoutParams dividerLP = (MarginLayoutParams) mDivider.getLayoutParams();

        if (mDivider.getBackground() != null && dividerHeight != -1)
            dividerLP.height = dividerHeight;

        float maxShadowSize = mSearchBackground.getMaxShadowSize();
        float cornerRadius = mSearchBackground.getCornerRadius();
        int horizontalPadding = (int) (RoundRectDrawableWithShadow.calculateHorizontalPadding(
                maxShadowSize, cornerRadius, false) + .5f);

        dividerLP.setMargins(horizontalPadding, dividerLP.topMargin, horizontalPadding, dividerLP.bottomMargin);
        mDivider.setLayoutParams(dividerLP);

        // Content inset
        MarginLayoutParams searchParams = (MarginLayoutParams) mSearchInput.getLayoutParams();

        int contentInsetStart = a.getDimensionPixelSize(R.styleable.FloatingSearchView_contentInsetStart,
                MarginLayoutParamsCompat.getMarginStart(searchParams));
        int contentInsetEnd = a.getDimensionPixelSize(R.styleable.FloatingSearchView_contentInsetEnd,
                MarginLayoutParamsCompat.getMarginEnd(searchParams));

        MarginLayoutParamsCompat.setMarginStart(searchParams, contentInsetStart);
        MarginLayoutParamsCompat.setMarginEnd(searchParams, contentInsetEnd);

        // anything else
        setContentBackgroundColor(a.getColor(R.styleable.FloatingSearchView_fsv_contentBackgroundColor, DEFAULT_CONTENT_COLOR));
        setRadius(a.getDimensionPixelSize(R.styleable.FloatingSearchView_fsv_cornerRadius, ViewUtils.dpToPx(DEFAULT_RADIUS)));
        setHint(a.getString(R.styleable.FloatingSearchView_android_hint));

        a.recycle();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupViews() {

        mSearchContainer.setLayoutTransition(getDefaultLayoutTransition());

        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
        mSearchContainer.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);

        mSearchContainer.setBackground(mSearchBackground);
        mSearchContainer.setMinimumHeight((int) mSearchBackground.getMinHeight());
        mSearchContainer.setMinimumWidth((int) mSearchBackground.getMinWidth());

        mRecyclerView.addItemDecoration(mCardDecorator);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setVisibility(View.INVISIBLE);

        mBackgroundDrawable = getBackground();

        if (mBackgroundDrawable != null)
            mBackgroundDrawable = mBackgroundDrawable.mutate();
        else
            mBackgroundDrawable = new ColorDrawable(DEFAULT_BACKGROUND_COLOR);

        setBackground(mBackgroundDrawable);
        mBackgroundDrawable.setAlpha(0);

//        mNavButtonView.setOnClickListener(v -> {
//            if(mNavigationClickListener != null)
//                mNavigationClickListener.onNavigationClick();
//        });

        setOnTouchListener((v, event) -> {
            if (!isActivated()) return false;
            setActivated(false);
            return true;
        });

        mSearchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus != isActivated()) setActivated(hasFocus);
        });

        mSearchInput.setOnKeyListener((view, keyCode, keyEvent) -> {
            if (keyCode != KeyEvent.KEYCODE_ENTER) return false;
            setActivated(false);
            return true;
        });

        btnClear.setOnClickListener(v -> {
            setText(null);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        });
    }

    public void setRadius(float radius) {
        mSearchBackground.setCornerRadius(radius);
        mCardDecorator.setCornerRadius(radius);
    }

    public void setContentBackgroundColor(@ColorInt int color) {
        mSearchBackground.setColor(color);
        mCardDecorator.setBackgroundColor(color);
    }

    public void setOnSearchListener(final OnSearchListener listener) {
        mSearchInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_ENTER) return false;
            listener.onSearchAction(mSearchInput.getText());
            return true;
        });
    }


    public CharSequence getText() {
        return mSearchInput.getText();
    }

    public void setText(CharSequence text) {
        mSearchInput.setText(text);
    }

    public void setHint(CharSequence hint) {
        mSearchInput.setHint(hint);
    }

    @Override
    public void setActivated(boolean activated) {
        if (activated == isActivated()) return;
        super.setActivated(activated);

        boolean textEmpty = getText().length() == 0;

        if (activated) {
            mSearchInput.requestFocus();
            ViewUtils.showSoftKeyboardDelayed(mSearchInput, 100);
            btnClear.setVisibility(textEmpty ? VISIBLE : GONE);
        } else {
            requestFocus();
            ViewUtils.closeSoftKeyboard(mActivity);
        }


        if (mFocusListener != null)
            mFocusListener.onFocusChanged(activated);

        fadeIn(activated);
        updateSuggestionsVisibility();
    }

    public void setOnSearchFocusChangedListener(OnSearchFocusChangedListener focusListener) {
        mFocusListener = focusListener;
    }

    public void addTextChangedListener(TextWatcher textWatcher) {
        mSearchInput.addTextChangedListener(textWatcher);
    }

    public void removeTextChangedListener(TextWatcher textWatcher) {
        mSearchInput.removeTextChangedListener(textWatcher);
    }

    public void setAdapter(CustomRecyclerView.Adapter<? extends CustomRecyclerView.ViewHolder> adapter) {
        CustomRecyclerView.Adapter<? extends CustomRecyclerView.ViewHolder> old = getAdapter();
        if (old != null) old.unregisterAdapterDataObserver(mAdapterObserver);
        adapter.registerAdapterDataObserver(mAdapterObserver);
        mRecyclerView.setAdapter(adapter);
    }

    public void setItemAnimator(CustomRecyclerView.ItemAnimator itemAnimator) {
        mRecyclerView.setItemAnimator(itemAnimator);
    }


    @Nullable
    public CustomRecyclerView.Adapter<? extends CustomRecyclerView.ViewHolder> getAdapter() {
        return (CustomRecyclerView.Adapter<? extends CustomRecyclerView.ViewHolder>) mRecyclerView.getAdapter();
    }

    protected LayoutTransition getDefaultLayoutTransition() {
        return new LayoutTransition();
    }

    private void fadeIn(boolean enter) {
        ValueAnimator backgroundAnim = ObjectAnimator.ofInt(mBackgroundDrawable, "alpha", enter ? 255 : 0);
        backgroundAnim.setDuration(enter ? DEFAULT_DURATION_ENTER : DEFAULT_DURATION_EXIT);
        backgroundAnim.setInterpolator(enter ? DECELERATE : ACCELERATE);
        backgroundAnim.start();
    }

    private int getSuggestionsCount() {
        CustomRecyclerView.Adapter<? extends CustomRecyclerView.ViewHolder> adapter = getAdapter();
        if (adapter == null) return 0;
        return adapter.getItemCount();
    }

    private void updateSuggestionsVisibility() {
        showSuggestions(isActivated() && getSuggestionsCount() > 0);
    }

    private boolean suggestionsShown() {
        return mSuggestionsShown;
    }

    private void showSuggestions(final boolean show) {
        if (show == suggestionsShown()) return;

        mSuggestionsShown = show;

        int childCount = mRecyclerView.getChildCount();
        int translation = 0;

        final Runnable endAction = () -> {
            if (show)
                updateDivider();
            else {
                showDivider(false);
                mRecyclerView.setVisibility(View.INVISIBLE);
                mRecyclerView.setTranslationY(-mRecyclerView.getHeight());
            }
        };

        if (show) {
            updateDivider();
            mRecyclerView.setVisibility(VISIBLE);
            if (mRecyclerView.getTranslationY() == 0)
                mRecyclerView.setTranslationY(-mRecyclerView.getHeight());
        } else if (childCount > 0)
            translation = -mRecyclerView.getChildAt(childCount - 1).getBottom();
        else
            showDivider(false);

        ViewPropertyAnimatorCompat listAnim = ViewCompat.animate(mRecyclerView)
                .translationY(translation)
                .setDuration(show ? DEFAULT_DURATION_ENTER : DEFAULT_DURATION_EXIT)
                .setInterpolator(show ? DECELERATE : ACCELERATE)
                .withLayer()
                .withEndAction(endAction);

        if (show || childCount > 0)
            listAnim.start();
        else
            endAction.run();
    }

    private void showDivider(boolean visible) {
        mDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
        int shadows = TOP | LEFT | RIGHT;
        if (!visible) shadows |= BOTTOM;
        mSearchBackground.setShadow(shadows);
    }

    private void updateDivider() {
        showDivider(isActivated() && getSuggestionsCount() > 0);
    }

    @NonNull
    private Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        throw new IllegalStateException();
    }

}
