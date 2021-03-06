package pl.bydgoszcz.guideme.autofittextview;

import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

public class AutoFitScrollView {
    private static final int MAX_STEPS_COUNT = 20;
    private WeakReference<ScrollView> scrollViewReference;
    private WeakReference<ViewGroup> internalLayoutReference;

    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener;

    Logger logger;

    private boolean inChanging = false;
    private boolean isBlockedScrolling;

    private List<Step> steps;

    @NonNull
    public static AutoFitScrollView with(ScrollView scrollView, ViewGroup childView) {
        final AutoFitScrollView autoFitTextView = new AutoFitScrollView();
        autoFitTextView.scrollViewReference = new WeakReference<>(scrollView);
        autoFitTextView.internalLayoutReference = new WeakReference<>(childView);
        autoFitTextView.logger = Logger.getLogger("AutoFitScrollView");
        autoFitTextView.initialize();
        return autoFitTextView;
    }

    public void recycle() {
        final ScrollView scrollView = scrollViewReference.get();
        if (scrollView != null) {
            scrollView.setOnTouchListener(null);
            scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
        }
    }

    public void reset(){
        if (steps != null){
            steps.clear();
        }
    }

    private void initialize() {
        onGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                AutoFitScrollView.this.onGlobalLayout();
            }
        };
        final View.OnTouchListener onTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return isBlockedScrolling;
            }
        };

        final ScrollView scrollView = scrollViewReference.get();

        if (scrollView != null) {
            scrollView.setOnTouchListener(onTouchListener);
            scrollView.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
        }
    }

    private void onGlobalLayout() {
        logger.fine("onGlobalLayout");

        final ScrollView scrollView = scrollViewReference.get();
        final ViewGroup internalLayout = internalLayoutReference.get();

        if (scrollView != null && internalLayout != null && !inChanging) {
            process(scrollView, internalLayout);
        }
    }

    private void process(@NonNull ScrollView scrollView, @NonNull ViewGroup internalLayout) {
        logger.fine("process ");

        boolean processed = false;

        if (!scaleDown(scrollView, internalLayout)) {
            if (isBlockedScrolling) {
                processed = scaleUp(scrollView, internalLayout);
            }
        } else {
            processed = true;
        }

        if (!processed) {
            logger.fine("nth todo");

            isBlockedScrolling = true;
        }
    }

    private boolean scaleUp(@NonNull ScrollView scrollView, @NonNull ViewGroup internalLayout) {
        // checking
        final int childrenHeightSum = getChildrenHeightSum(internalLayout);
        final int containerHeight = scrollView.getMeasuredHeight();
        final int toleranceHeight = (int) (scrollView.getMeasuredHeight() * 0.01f);

        if (childrenHeightSum + toleranceHeight < containerHeight) {
            logger.fine("scaleUp processing");
            // inputs
            final float internalScaleY = internalLayout.getScaleY();
            final int containerWidth = scrollView.getMeasuredWidth();

            // calculations
            final int deltaHeight = containerHeight - childrenHeightSum;
            final float diffProportion = deltaHeight * 1.0f / containerHeight;

            // outputs
            final float newScale = internalScaleY * (1 + diffProportion);
            final int newContainerWidth = (int) Math.floor(containerWidth / newScale);

            return resize(scrollView, internalLayout, newScale, newContainerWidth, false);
        }
        return false;
    }

    private boolean scaleDown(@NonNull ScrollView scrollView, @NonNull ViewGroup internalLayout) {
        // inputs
        final int containerHeight = scrollView.getMeasuredHeight();
        final int containerWidth = scrollView.getMeasuredWidth();
        final int internalHeight = internalLayout.getMeasuredHeight();
        final int scaledHeight = (int) Math.floor(internalHeight * internalLayout.getScaleY());

        if (containerHeight < scaledHeight) {
            logger.fine("scaleDown processing");

            // outputs
            final float newScale = 1.0f - 1.0f * (internalHeight - containerHeight) / internalHeight;
            final int newContainerWidth = (int) Math.floor(containerWidth / newScale);

            return resize(scrollView, internalLayout, newScale, newContainerWidth, true);
        }
        return false;
    }

    private boolean resize(@NonNull ScrollView scrollView, @NonNull ViewGroup internalLayout, float newScale, int newContainerWidth, boolean forceChange) {
        logger.fine(String.format("resize to %s", newScale));
        // flag

        final Step step = new Step(newScale, newContainerWidth);
        if (addStep(step, forceChange)) {
            inChanging = true;
            try {
                internalLayout.setPivotX(0);
                internalLayout.setPivotY(0);
                internalLayout.setScaleY(newScale);
                internalLayout.setScaleX(newScale);

                scrollView.scrollTo(0, 0);
                changeWidth(scrollView, internalLayout, newContainerWidth);

            } finally {
                inChanging = false;
            }
            return true;
        } else {
            logger.fine(String.format("resize canceled, step already exists %s", step));
            return false;
        }
    }

    private void changeWidth(@NonNull ScrollView scrollView, @NonNull ViewGroup internalLayout, int newWidth) {
        logger.fine(String.format("changeWidth to %s", newWidth));

        final ScrollView.LayoutParams params = new ScrollView.LayoutParams(newWidth, scrollView.getHeight());
        internalLayout.setLayoutParams(params);
        scrollView.updateViewLayout(internalLayout, params);

        updateChildren(internalLayout);
    }

    private boolean addStep(@NonNull Step newStep, boolean forceChange) {
        if (steps == null) {
            steps = new LinkedList<>();
        }
        if (forceChange) {
            addStepToList(newStep);
            return true;
        } else {
            boolean found = false;
            for (Step step : steps) {
                if (step.equals(newStep)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                addStepToList(newStep);
            }
            return !found;
        }
    }

    private void addStepToList(Step newStep) {
        if (steps.size() >= MAX_STEPS_COUNT) {
            steps.remove(0);
        }
        steps.add(newStep);
    }

    private void updateChildren(@NonNull ViewGroup internalLayout) {
        View child;

        for (int i = 0; i < internalLayout.getChildCount(); i++) {
            child = internalLayout.getChildAt(i);
            child.setLayoutParams(child.getLayoutParams());

            internalLayout.updateViewLayout(child, child.getLayoutParams());
        }
    }

    private int getChildrenHeightSum(@NonNull ViewGroup internalLayout) {
        int sum = 0;
        for (int i = 0; i < internalLayout.getChildCount(); i++) {
            sum += internalLayout.getChildAt(i).getMeasuredHeight();
        }
        return (int) (sum * internalLayout.getScaleY());
    }

    private static class Step {
        private final float scale;
        private final int width;

        public Step(float scale, int width) {
            this.scale = scale;
            this.width = width;
        }

        public boolean equals(@NonNull Step step) {
            return width == step.width && scale == step.scale;
        }
    }
}
