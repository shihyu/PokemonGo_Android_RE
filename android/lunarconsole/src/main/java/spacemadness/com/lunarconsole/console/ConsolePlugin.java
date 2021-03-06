//
//  ConsolePlugin.java
//
//  Lunar Unity Mobile Console
//  https://github.com/SpaceMadness/lunar-unity-console
//
//  Copyright 2016 Alex Lementuev, SpaceMadness.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

package spacemadness.com.lunarconsole.console;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import com.unity3d.player.UnityPlayer;

import java.lang.ref.WeakReference;
import java.util.List;

import spacemadness.com.lunarconsole.R;
import spacemadness.com.lunarconsole.core.Destroyable;
import spacemadness.com.lunarconsole.debug.Log;
import spacemadness.com.lunarconsole.ui.gestures.GestureRecognizer;
import spacemadness.com.lunarconsole.ui.gestures.GestureRecognizerFactory;

import static android.widget.FrameLayout.LayoutParams;
import static spacemadness.com.lunarconsole.console.Console.Options;
import static spacemadness.com.lunarconsole.console.ConsoleLogType.*;
import static spacemadness.com.lunarconsole.utils.ThreadUtils.*;
import static spacemadness.com.lunarconsole.utils.UIUtils.*;
import static spacemadness.com.lunarconsole.ui.gestures.GestureRecognizer.OnGestureListener;
import static spacemadness.com.lunarconsole.debug.Tags.*;

public class ConsolePlugin implements
        Destroyable,
        ConsoleView.Listener,
        WarningView.Listener
{
    private static final String SCRIPT_MESSAGE_CONSOLE_OPEN  = "ConsoleOpenCallback";
    private static final String SCRIPT_MESSAGE_CONSOLE_CLOSE = "ConsoleCloseCallback";

    private static ConsolePlugin instance;

    private Console console;
    private final ConsolePluginImp pluginImp;
    private final String version;

    private ConsoleView consoleView;
    private WarningView warningView;

    private final WeakReference<Activity> activityRef;
    private final GestureRecognizer gestureDetector;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Static initialization

    /* Since the game can log many console entries on the secondary thread, we need an efficient
     * way to batch them on the main thread
     */
    private static final ConsoleEntryDispatcher entryDispatcher = new ConsoleEntryDispatcher(
            new ConsoleEntryDispatcher.OnDispatchListener()
    {
        @Override
        public void onDispatchEntries(List<ConsoleEntry> entries)
        {
            if (instance != null)
            {
                instance.logEntries(entries);
            }
            else
            {
                Log.e("Can't log message: plugin instance is not initialized");
            }
        }
    });

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Static interface

    public static void init(String version, String target, int capacity, int trim, String gesture)
    {
        Activity activity = UnityPlayer.currentActivity;
        init(activity, version, capacity, trim, new UnityPluginImp(activity, target), gesture);
    }

    public static void init(Activity activity, String version, int capacity, int trim, String gesture)
    {
        init(activity, version, capacity, trim, new DefaultPluginImp(activity), gesture);
    }

    private static void init(final Activity activity,
                             final String version,
                             final int capacity,
                             final int trim,
                             final ConsolePluginImp pluginImp,
                             final String gesture)
    {
        if (isRunningOnMainThread())
        {
            init0(activity, version, capacity, trim, pluginImp, gesture);
        }
        else
        {
            Log.d(PLUGIN,
                    "Tried to initialize plugin on the secondary thread. Scheduling on UI-thread...");

            runOnUIThread(new Runnable()
            {
                @Override
                public void run()
                {
                    init0(activity, version, capacity, trim, pluginImp, gesture);
                }
            });
        }
    }

    private static void init0(Activity activity,
                              String version,
                              int capacity,
                              int trim,
                              ConsolePluginImp pluginImp,
                              String gesture)
    {
        try
        {
            if (instance == null)
            {
                Log.d(PLUGIN, "Initializing plugin instance (%s): %d", version, capacity);

                instance = new ConsolePlugin(activity, version, capacity, trim, pluginImp, gesture);
                instance.enableGestureRecognition();
            }
            else
            {
                Log.w("Plugin instance already initialized");
            }
        }
        catch (Exception e)
        {
            Log.e(e, "Can't initialize plugin instance");
        }
    }

    public static void shutdown()
    {
        if (isRunningOnMainThread())
        {
            shutdown0();
        }
        else
        {
            Log.d(PLUGIN, "Tried to shutdown plugin on the secondary thread. Scheduling on UI-thread...");

            runOnUIThread(new Runnable()
            {
                @Override
                public void run()
                {
                    shutdown0();
                }
            });
        }
    }

    private static void shutdown0()
    {
        try
        {
            if (instance != null)
            {
                instance.destroy();
                instance = null;
            }
        }
        catch (Exception e)
        {
            Log.e(e, "Error while shutting down the plugin");
        }
    }

    public static void logMessage(String message, String stackTrace, int logType)
    {
        entryDispatcher.add(new ConsoleEntry((byte) logType, message, stackTrace));
    }

    public static void show()
    {
        if (isRunningOnMainThread())
        {
            show0();
        }
        else
        {
            runOnUIThread(new Runnable()
            {
                @Override
                public void run()
                {
                    show0();
                }
            });
        }
    }

    private static void show0()
    {
        if (instance != null)
        {
            instance.showConsole();
        }
        else
        {
            Log.w("Can't show console: instance is not initialized");
        }
    }

    public static void hide()
    {
        if (isRunningOnMainThread())
        {
            hide0();
        }
        else
        {
            runOnUIThread(new Runnable()
            {
                @Override
                public void run()
                {
                    hide0();
                }
            });
        }
    }

    private static void hide0()
    {
        if (instance != null)
        {
            instance.hideConsole();
        }
        else
        {
            Log.w("Can't hide console: instance is not initialized");
        }
    }

    public static void clear()
    {
        if (isRunningOnMainThread())
        {
            clear0();
        }
        else
        {
            runOnUIThread(new Runnable()
            {
                @Override
                public void run()
                {
                    clear0();
                }
            });
        }
    }

    private static void clear0()
    {
        if (instance != null)
        {
            instance.clearConsole();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Constructor

    private ConsolePlugin(Activity activity, String version, int capacity, int trim, ConsolePluginImp pluginImp, String gesture)
    {
        if (activity == null)
        {
            throw new NullPointerException("Context is null");
        }

        if (version == null)
        {
            throw new NullPointerException("Version is null");
        }

        this.version = version;
        this.pluginImp = pluginImp;

        Options options = new Options(capacity);
        options.setTrimCount(trim);
        console = new Console(options);
        activityRef = new WeakReference<>(activity);

        gestureDetector = GestureRecognizerFactory.create(activity, gesture);
        gestureDetector.setListener(new OnGestureListener()
        {
            @Override
            public void onGesture(GestureRecognizer gestureRecognizer)
            {
                showConsole();
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Destroyable

    @Override
    public void destroy()
    {
        disableGestureRecognition();

        console.destroy();
        entryDispatcher.cancelAll();

        Log.d(PLUGIN, "Plugin destroyed");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Console

    private void logEntries(List<ConsoleEntry> entries)
    {
        for (int i = 0; i < entries.size(); ++i)
        {
            ConsoleEntry entry = entries.get(i);

            // add to console
            console.logMessage(entry);

            // show warning
            if (isErrorType(entry.type) && !isConsoleShown())
            {
                showWarning(entry.message);
            }
        }
    }

    private boolean showConsole()
    {
        try
        {
            if (consoleView == null)
            {
                Log.d(CONSOLE, "Show console");

                final Activity activity = getActivity();
                if (activity == null)
                {
                    Log.e("Can't show console: activity reference is lost");
                    return false;
                }

                final FrameLayout rootLayout = getRootLayout(activity);

                consoleView = new ConsoleView(activity, console);
                consoleView.setListener(this);

                consoleView.requestFocus();

                LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                rootLayout.addView(consoleView, params);

                Animation animation = AnimationUtils.loadAnimation(activity, R.anim.lunar_console_slide_in_top);
                consoleView.startAnimation(animation);

                consoleView.notifyOpen();

                // don't handle gestures if console is shown
                disableGestureRecognition();

                return true;
            }

            Log.w("Console is show already");
            return false;
        }
        catch (Exception e)
        {
            Log.e(e, "Can't show console");
            return false;
        }
    }

    private boolean hideConsole()
    {
        try
        {
            if (consoleView != null)
            {
                Log.d(CONSOLE, "Hide console");

                Activity activity = getActivity();
                if (activity != null)
                {
                    Animation animation = AnimationUtils.loadAnimation(activity, R.anim.lunar_console_slide_out_top);
                    animation.setAnimationListener(new Animation.AnimationListener()
                    {
                        @Override
                        public void onAnimationStart(Animation animation)
                        {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation)
                        {
                            removeConsoleView();
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation)
                        {

                        }
                    });
                    consoleView.startAnimation(animation);
                }
                else
                {
                    removeConsoleView();
                }

                return true;
            }
        }
        catch (Exception e)
        {
            Log.e(e, "Can't hide console");
        }

        return false;
    }

    private void removeConsoleView()
    {
        if (consoleView != null)
        {
            ViewParent parent = consoleView.getParent();
            if (parent instanceof ViewGroup)
            {
                ((ViewGroup) parent).removeView(consoleView);
            }
            else
            {
                Log.e("Can't remove console view: unexpected parent " + parent);
            }

            consoleView.destroy();
            consoleView = null;

            enableGestureRecognition();
        }
    }

    private void clearConsole()
    {
        try
        {
            console.clear();
        }
        catch (Exception e)
        {
            Log.e(e, "Can't clear console");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Error warning

    private void showWarning(final String message)
    {
        try
        {
            if (warningView == null)
            {
                Log.d(WARNING_VIEW, "Show warning");

                final Activity activity = getActivity();
                if (activity == null)
                {
                    Log.e("Can't show warning: activity reference is lost");
                    return;
                }

                final FrameLayout rootLayout = getRootLayout(activity);

                warningView = new WarningView(activity);
                warningView.setListener(this);

                rootLayout.addView(warningView);
            }

            warningView.setMessage(message);
        }
        catch (Exception e)
        {
            Log.e(e, "Can't show warning");
        }
    }

    private void hideWarning()
    {
        if (warningView != null)
        {
            Log.d(WARNING_VIEW, "Hide warning");

            ViewParent parent = warningView.getParent();
            if (parent instanceof ViewGroup)
            {
                ViewGroup parentGroup = (ViewGroup) parent;
                parentGroup.removeView(warningView);
            }
            else
            {
                Log.e("Can't hide warning view: unexpected parent view " + parent);
            }

            warningView.destroy();
            warningView = null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // ConsoleView.Listener


    @Override
    public void onOpen(ConsoleView view)
    {
        sendNativeCallback(SCRIPT_MESSAGE_CONSOLE_OPEN);
    }

    @Override
    public void onClose(ConsoleView view)
    {
        hideConsole();
        sendNativeCallback(SCRIPT_MESSAGE_CONSOLE_CLOSE);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // WarningView.Listener

    @Override
    public void onDismissClick(WarningView view)
    {
        hideWarning();
    }

    @Override
    public void onDetailsClick(WarningView view)
    {
        hideWarning();
        showConsole();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Gesture recognition

    public void enableGestureRecognition()
    {
        Log.d(GESTURES, "Enable gesture recognition");

        View view = pluginImp.getTouchRecepientView();
        if (view == null)
        {
            Log.w("Can't enable gesture recognition: touch view is null");
            return;
        }

        view.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                gestureDetector.onTouchEvent(event);
                return false; // do not block touch events!
            }
        });
    }

    public void disableGestureRecognition()
    {
        Log.d(GESTURES, "Disable gesture recognition");

        View view = pluginImp.getTouchRecepientView();
        if (view != null)
        {
            view.setOnTouchListener(null);
        }
        else
        {
            Log.w("Can't disable gesture recognition: touch view is null");
        }
    }

    private Activity getActivity()
    {
        return activityRef.get();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Native callbacks

    private void sendNativeCallback(String name)
    {
        pluginImp.sendUnityScriptMessage(name, "");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters

    public boolean isConsoleShown()
    {
        return consoleView != null;
    }

    public static String getVersion()
    {
        return instance != null ? instance.version : "?.?.?";
    }

    private static Console getConsole()
    {
        return instance.console;
    }

    public static int getCapacity()
    {
        return getConsole().getCapacity();
    }

    public static int getTrimSize()
    {
        return getConsole().getTrimSize();
    }

    public static void setCapacity(int capacity)
    {
        Options options = new Options(capacity);
        options.setTrimCount(getConsole().getTrimSize());
        instance.console = new Console(options);
    }

    public static void setTrimSize(int trimCount)
    {
        Options options = new Options(getConsole().getCapacity());
        options.setTrimCount(trimCount);
        instance.console = new Console(options);
    }
}
