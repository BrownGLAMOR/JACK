/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jack.scheduler;

import java.util.logging.Logger;

public abstract class Task implements Runnable
{
    /** A new task that has not started is in this state */
    public static final int STATE_NEW = 1;

    /** An task that is running is in this state */
    public static final int STATE_RUNNING = 2;

    /** An task that is running and can be ended is in this state */
    public static final int STATE_ENDABLE = 3;

    /** An task that is in the process of ending is in this state */
    public static final int STATE_ENDING = 4;

    /** An task that is ended is in this state */
    public static final int STATE_ENDED = 5;

    /** The id of the session this state is part of */
    protected int sessionId = 0;

    /** The unique id of this state within the session */
    protected final int taskId;

    /** The current state of the task */
    private int state = STATE_NEW;

    /** The lock that the state is synchronized on */
    private Object stateLock = new Object();

    /** Logger for writing log messages */
    private final Logger LOGGER = Logger.getLogger(Task.class.getName());

    /**
     * Constructs a state with the specified identification.
     * @param taskId The unique identified of this state
     */
    public Task(int taskId) {
        this.taskId = taskId;
    }

    /**
     * Sets the session identifier that this state is a part of.
     * @param newSessionId The new session that this state is a part of
     */
    public final void setSessionId(int newSessionId) {
        sessionId = newSessionId;
    }

    /**
     * This function returns the session identifier for this state.
     * @return The intiger identifier of this task's session
     */
    public final int getSessionId() {
        return sessionId;
    }

    /**
     * This function returns the unique identifier of this state. Each state
     * is assigned an integer identifier suring constructor. This value can be
     * used to uniquely identify the state within a schedule or in a log file.
     * @return The integer identifier of this state
     */
    public final int getTaskId() {
        return taskId;
    }

    /**
     * This function sets the lock object used to protect the current state of
     * the task. Each task is initially constructed with its own lock,
     * which allows the state to be queried and set in a multithreaded
     * environment. This function is useful when multiple taska need to be
     * synchronized on a single lock object, such as when they are managed by a
     * schedule.
     * @param newStateLock The new lock to synchronize the task state on
     */
    public final void setStateLock(Object newStateLock) {
        stateLock = newStateLock;
    }

    /**
     * This function returns the current state of this task. The state is one
     * of STATE_NEW, STATE_RUNNING, STATE_ENDABLE, STATE_ENDING, or STATE_ENDED.
     * This function is thread safe.
     * @return The current state of this task
     */
    public final int getState() {
        synchronized (stateLock) {
            return state;
        }
    }

    /**
     * This function tries to end this task. An task can end if it is in
     * the STATE_ENDABLE state. If this function succeeds it returns true and
     * moves the task from STATE_ENDABLE into STATE_ENDING, otherwise it
     * returns false. This function should be used by a scheduler to end
     * tasks which may be synchronized with other tasks. This function is
     * thread safe.
     * @return True on success and false otherwise
     */
    public final boolean tryEnd() {
        return setState(STATE_ENDING);
    }

    /**
     * This function tries to resume an task. An task can be resumed if it
     * is in the STATE_ENDABLE state. If this function succees it returns true
     * and moves the task from STATE_ENDABLE into STATE_RUNNING, otherwise it
     * returns false. This function should be used by an task subclass that
     * for whatever reason should no longer be ended. This function is thread
     * safe.
     * @return True on success and false on failure
     */
    public final boolean tryResume() {
        return setState(STATE_RUNNING);
    }

    /**
     * This function blocks until the current task moves into STATE_ENDED. It
     * should be used with care as this may take an undefined amount of time. In
     * general it should be called after a call to tryEnd has returned true.
     * This function is thread safe.
     */
    public final void waitForEnd() {
        synchronized (stateLock) {
            try {
                while (state != STATE_ENDED) {
                    stateLock.wait();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * This function trys to set the current task state to STATE_ENDABLE. It
     * should be called by subclasses when they are able to be ended. It returns
     * true if the task is currently in STATE_RUNNING and false otherwise.
     * @return True on success and false on failure
     */
    protected final boolean tryEndable() {
        return setState(STATE_ENDABLE);
    }

    /**
     * This function attempts to transition the task to the specified state.
     * This function represents the core task state machine. If the task
     * can transition to the specified state this function will return true
     * otherwise it will return false. This function is thread safe.
     * @param newState The state to try and transitions into
     * @return True on success and false on failure
     */
    protected final boolean setState(int newState) {
        synchronized (stateLock) {
            switch (state) {

                // Tasks in STATE_NEW can only transition to STATE_RUNNING.
                // This happens the first time that the tasks run method is
                // called. Tasks in STATE_RUNNING can send and receive
                // message from their clients.

                case STATE_NEW:
                    if (newState != STATE_RUNNING) {
                        return false;
                    }
                    LOGGER.fine(String.format("Task %d running\n", taskId));
                    state = STATE_RUNNING;
                    stateLock.notify();
                    return true;

                // Tasks in STATE_RUNNING can only transition to
                // STATE_ENDABLE. This happens when an task subclass
                // determines that it can be ended. Task in STATE_ENDABLE can
                // still send and receive message from their clients.

                case STATE_RUNNING:
                    if (newState != STATE_ENDABLE) {
                        return false;
                    }
                    LOGGER.fine(String.format("Task %d endable\n", taskId));
                    state = STATE_ENDABLE;
                    stateLock.notify();
                    return true;

                // Tasks in STATE_ENDABLE can transition either to
                // STATE_RUNNING or STATE_ENDING. Transitioning back to
                // STATE_RUNNING can be done by a subclass by calling tryResume,
                // and transitioning to STATE_ENDING can be done by a schedular
                // by calling tryEnd.

                case STATE_ENDABLE:
                    if (newState == STATE_RUNNING) {
                        LOGGER.fine(String.format("Task %d running\n", taskId));
                        state = STATE_RUNNING;
                        stateLock.notify();
                        return true;
                    }
                    if (newState == STATE_ENDING) {
                        LOGGER.fine(String.format("Task %d ending\n", taskId));
                        state = STATE_ENDING;
                        stateLock.notify();
                        return true;
                    }
                    return false;

                // Tasks in STATE_ENDING can no longer send and receive
                // messages from their clients, but they have no been officially
                // resolved. From here the only state that can be transitioned
                // to is STATE_ENDED.

                case STATE_ENDING:
                    if (newState != STATE_ENDED) {
                        return false;
                    }
                    LOGGER.fine(String.format("Task %d ended\n", taskId));
                    state = STATE_ENDED;
                    stateLock.notify();
                    return true;

                // STATE_ENDED is a terminal state, and once here a task is
                // essentially useless.

                default:
                case STATE_ENDED:
                    return false;
            }
        }
    }

    /**
     * Classes that subclass Task should implement the run function to perform
     * their desired behavior.
     */
    public abstract void run();
}
