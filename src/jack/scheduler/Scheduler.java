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

import java.lang.IllegalArgumentException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * The Scheduler class represents a general scheduling of tasks as two
 * dependency graphs. One set of dependencies enumerates the starting conditions
 * for a particular task and the other set enumerates the ending conditions.
 * The tasks are started and stopped by the schedular based on these
 * dependencies.
 */
public class Scheduler {

    /** Adjacency list of an tasks starting dependencies */
    private final Map<Integer, Set<Integer>> startDepends
                                    = new HashMap<Integer, Set<Integer>>();

    /** Adjacency list of an tasks ending dependencies */
    private final Map<Integer, Set<Integer>> endDepends
                                    = new HashMap<Integer, Set<Integer>>();

    /** Lock to synchronize tasks */
    private final Object stateLock = new Object();

    /**
     * Adds the specified task to the schedule. If a task with the same
     * ID has already been added to the schedule then this function does not
     * change the schedule and returns false.
     * @param task The task to add to the schedule
     * @return true if the task is added to the schedule
     */
    public boolean addAuction(int taskId) {
        if (startDepends.containsKey(taskId)) {
            return false;
        }

        startDepends.put(taskId, new HashSet<Integer>());
        endDepends.put(taskId, new HashSet<Integer>());
        return true;
    }

    /**
     * Adds the specified starting dependency to the schedule. A starting
     * dependency constrains task1 from starting until task2 has finished.
     * Both task1 and task2 must be present in the schedule or this will
     * throw an IllegalArgumentException.
     * @param task1 The task to constrain
     * @param task2 The task that must end before other starts
     */
    public void addStartDepend(int taskId1, int taskId2) {
        if (!startDepends.containsKey(taskId1)
                || !startDepends.containsKey(taskId2)) {
            throw new IllegalArgumentException("no such taskId");
        }

        if (taskId1 == taskId2) {
            throw new IllegalArgumentException("an task cannot depend on itself");
        }

        startDepends.get(taskId1).add(taskId2);
    }

    /**
     * Returns the starting dependencies for the given taskId. If the
     * taskId is invalid this function throws an IllegalArgumentException.
     * @param taskId The unique identifier of an task
     * @return The set of taskIds that this task depends on
     */
    public Set<Integer> getStartDepends(int taskId) {
        if (!startDepends.containsKey(taskId)) {
            throw new IllegalArgumentException("no such taskId");
        }

        return startDepends.get(taskId);
    }

    /**
     * Adds the specified ending dependency to the schedule. An ending
     * dependency constrains task1 from being ended until task2 is
     * endable. Both task1 and task2 must be present in the schedule or
     * this will throw an IllegalArgumentException.
     * @param task1 The task to constrain
     * @param task2 The task that must be endable before the other ends
     */
    public void addEndDepend(int taskId1, int taskId2) {
        if (!endDepends.containsKey(taskId1)
                || !endDepends.containsKey(taskId2)) {
            throw new IllegalArgumentException("no such taskId");
        }

        if (taskId1 == taskId2) {
            throw new IllegalArgumentException("an task cannot depend on itself");
        }

        endDepends.get(taskId1).add(taskId2);
    }

    /**
     * Returns the ending dependences for the given taskId. If the
     * taskId is invalid this function throws an IllegalArgumentException.
     * @param taskId The unique identifier of an task
     * @return The set of taskIds that this task depends on
     */
    public Set<Integer> getEndDepends(int taskId) {
        if (!endDepends.containsKey(taskId)) {
            throw new IllegalArgumentException("no such taskId");
        }

        return endDepends.get(taskId);
    }

    /**
     * Returns a topological sorting of the starting dependency graph. This
     * function uses Kahn's algorithm to determine the sort order. It only
     * considered starting dependencies, and does not take ending dependencies
     * into account. The sort that is returned is not necessarily the same order
     * that the final task will be executed in.
     * @return The topological sort over starting dependencies
     */
    public List<Integer> getTopoSort() {

        // Create a duplicate list of edges

        Map<Integer, Set<Integer>> edges =
            new HashMap<Integer, Set<Integer>>(startDepends);

        // Find all starting nodes and insert them into the queue

        LinkedList<Integer> queue = new LinkedList<Integer>();
        for (Map.Entry<Integer, Set<Integer>> entry : edges.entrySet()) {
            if (entry.getValue().isEmpty()) {
                queue.add(entry.getKey());
            }
        }

        // Kahn's algorithm

        List<Integer> sorted = new ArrayList<Integer>();
        while (!queue.isEmpty()) {
            int node = queue.remove();
            sorted.add(node);

            for (Map.Entry<Integer, Set<Integer>> entry : edges.entrySet()) {
                if (entry.getValue().remove(node)) {
                    if (entry.getValue().isEmpty()) {
                        queue.add(entry.getKey());
                    }
                }
            }
        }

        // Check for cycles

        for (Map.Entry<Integer, Set<Integer>> entry : edges.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sorted.clear();
                break;
            }
        }

        return sorted;
    }

    /** Prints an adjacency list representation of the schedule. */
    public void dump() {
        for (Map.Entry<Integer, Set<Integer>> entry : startDepends.entrySet()) {
            System.out.format("%d: { ", entry.getKey());
            for (int startDepend : entry.getValue()) {
                System.out.format("%d ", startDepend);
            }
            System.out.format("} { ");
            for (int endDepend : endDepends.get(entry.getKey())) {
                System.out.format("%d ", endDepend);
            }
            System.out.println("}");
        }
    }

    /**
     * This function executes the task schedule. The procedure for executing
     * the schedule is simple: try to end any endable tasks, start any startable
     * tasks, and repeat. This is done until there are no endable and startable
     * tasks remaining. Any task that is passed to this function which has
     * not been explicitly added tot he scheduler will be ignored.
     * @param tasks Map of tasks indexed by their unique identification
     *
     * TODO: This function does not check that a given schedule can be executed
     * before hand. It would be useful to try and topologically sort the
     * dependency graph beforehand. This is probably also a more efficient
     * approach to executing the schedule then the implementation below.
     */
    public <T extends Task> void execute(Map<Integer, T> tasks) {

        // Set the synchronization object for all tasks in the schedule. This
        // will allow us to synchronize execution of the schedule and individual
        // tasks without the tasks themselves knowing specifically about
        // the schedule that they are being run by.

        for (T task : tasks.values()) {
            task.setStateLock(stateLock);
        }

        // Create a thread pool to execute the tasks. We use a cached thread
        // pool here because it only gives us as many threads as we need to
        // request in order to execute the schedule without blocking.

        ExecutorService threadPool = Executors.newCachedThreadPool();

        synchronized (stateLock) {
            while (true) {

                // Get the set of endable tasks and try to end them

                Set<T> endableTasks = getEndables(tasks);
                for (T task : endableTasks) {
                    task.tryEnd();
                }

                // Wait for each of those tasks to end. This approach does
                // have some limitations in that while we are waiting, other
                // tasks that were running may become endable, but we will
                // not be able to immediately end them. The primary assumption
                // that goes into this approach is that tasks will generally
                // resolve quickly. If this is not the case the schedule will
                // still be executed correctly, but not necessarily efficiently.

                for (T task : endableTasks) {
                    task.waitForEnd();
                }

                // Get the set of startable tasks and execute them

                Set<T> startableAuctions = getStartables(tasks);
                for (T task : startableAuctions) {
                    threadPool.execute(task);
                }

                // Now chack if we have finished executing the task schedule.
                // If there are any tasks that we just started then we have
                // not finished. This check is necessary because the tasks
                // that we just started may not have moved into STATE_RUNNING.
                // Alternatively it may be better to include a waitForStart
                // function in the Task. That would allow us to just call
                // isEnded here.

                if (startableAuctions.isEmpty() && isEnded(tasks)) {
                    break;
                }

                // Now we block until we get a change in the number of endable
                // tasks. This is really what keys the actions of the
                // schedule. As long as no tasks move into STATE_ENDABLE,
                // then they cannot be ended and no other tasks can be
                // started.

                try {
                    while (endableTasks.equals(getEndables(tasks))) {
                        stateLock.wait();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Terminate the thread pool

        threadPool.shutdown();
    }

    /**
     * Returns the set of tasks that are endable. An endable task is not
     * only in STATE_ENDABLE, but also has met all of its ending dependencies.
     * To meet this requirement, each ending dependency must be in either
     * STATE_ENDABLE, STATE_ENDING, or STATE_ENDED.
     * @param tasks Map of tasks indexed by their unique identification
     * @return The set of endable tasks
     */
    private <T extends Task> Set<T> getEndables(Map<Integer, T> tasks) {
        Set<T> endables = new HashSet<T>();
        for (T task : tasks.values()) {

            // Ignore tasks that are not endable or have not been added to
            // the scheduler.

            if (task.getState() != Task.STATE_ENDABLE
                    || !endDepends.containsKey(task.getTaskId())) {
                continue;
            }

            // Check if the task has met all of its end dependencies

            boolean canEnd = true;
            for (int id : endDepends.get(task.getTaskId())) {
                if (!tasks.containsKey(id)
                        || tasks.get(id).getState()
                            < Task.STATE_ENDABLE) {
                    canEnd = false;
                    break;
                }
            }

            if (canEnd) {
                endables.add(task);
            }
        }
        return endables;
    }

    /**
     * Returns the set of tasks that are startable. A startable task is
     * currently in STATE_NEW and has also met all of its starting dependencies.
     * This requires that each starting dependency must be in STATE_ENDED. In
     * addition if there are any starting or ending dependencies of an task
     * that are not present in the map, then hat task will never be
     * considered startable.
     * @param tasks Map of tasks indexed by their unique identification
     * @return The set of startable tasks
     */
    private <T extends Task> Set<T> getStartables(Map<Integer, T> tasks) {
        Set<T> startables = new HashSet<T>();
        for (T task : tasks.values()) {

            // Ignore tasks that have not been started or have not been added
            // to the schduler.

            if (task.getState() != Task.STATE_NEW
                    || !startDepends.containsKey(task.getTaskId())) {
                continue;
            }

            // Check if the task has met all of its start dependencies

            boolean canStart = true;
            for (int id : startDepends.get(task.getTaskId())) {
                if (!tasks.containsKey(id)
                        || tasks.get(id).getState()
                            != Task.STATE_ENDED) {
                    canStart = false;
                    break;
                }
            }

            // In addition check that we *know* about all of this task's
            // ending dependencies. We do not want to accidentally start an
            // task that we can never end.

            if (canStart && tasks.keySet().containsAll(
                    endDepends.get(task.getTaskId()))) {
                startables.add(task);
            }
        }
        return startables;
    }

    /**
     * Returns true if the all of the tasks that have been started have also
     * been ended. This function alone does not determine if we have completed
     * executing the schedule.
     * @param tasks Map of tasks indexed by their unique identification
     */
    private <T extends Task> boolean isEnded(Map<Integer, T> tasks) {
        for (T task : tasks.values()) {
            switch (task.getState()) {
                case Task.STATE_RUNNING:
                case Task.STATE_ENDABLE:
                case Task.STATE_ENDING:
                    return false;
            }
        }
        return true;
    }
}
