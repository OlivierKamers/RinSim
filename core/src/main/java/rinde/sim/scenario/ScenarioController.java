package rinde.sim.scenario;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rinde.sim.core.Simulator;
import rinde.sim.core.TickListener;
import rinde.sim.event.Event;
import rinde.sim.event.EventDispatcher;
import rinde.sim.event.EventAPI;
import rinde.sim.event.Listener;
import rinde.sim.event.pdp.StandardType;

/**
 * A simulator controller represents single simulation run. This class is
 * intended for extension.
 * 
 * @author Rinde van Lon (rinde.vanlon@cs.kuleuven.be)
 * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be>
 * @since 2.0
 */
public abstract class ScenarioController implements TickListener, Listener {

	// TODO investigate if ConfigurationException can be replaced with
	// IllegalStateException

	public enum Type {
		SCENARIO_STARTED, SCENARIO_FINISHED;
	}

	protected static final Logger LOGGER = LoggerFactory.getLogger(ScenarioController.class);

	protected final Scenario scenario;
	private int ticks;
	private final EventDispatcher disp;

	private Simulator simulator;

	private Type status = null;

	/**
	 * <code>true</code> when user interface was defined. In ui mode
	 */
	private boolean uiMode;

	/**
	 * Create an instance of ScenarioController with defined {@link Scenario}
	 * and number of ticks till end. If the number of ticks is negative the
	 * simulator will run until the {@link Simulator#stop()} method is called.
	 * TODO refine documentation
	 * 
	 * @param scen to realize
	 * @param numberOfTicks when negative the number of tick is infinite
	 * @throws ConfigurationException on multiple problems that might occur
	 *             during configuration
	 */
	public ScenarioController(final Scenario scen, int numberOfTicks) throws ConfigurationException {
		if (scen == null) {
			throw new ConfigurationException("scenario cannot be null");
		}
		ticks = numberOfTicks;
		scenario = new Scenario(scen);
		disp = new EventDispatcher(merge(scenario.getPossibleEventTypes(), Type.values()));
		disp.addListener(this, scenario.getPossibleEventTypes());
	}

	/**
	 * Method that initializes the simulator using
	 * {@link ScenarioController#createSimulator()} and user interface (if
	 * defined) using {@link ScenarioController#createUserInterface()}. Must be
	 * called from within a constructor of specialized class.
	 * @throws ConfigurationException
	 */
	final protected void initialize() throws ConfigurationException {
		try {
			simulator = createSimulator();
		} catch (Exception e) {
			LOGGER.warn("exceptioin thrown during createSimulator()", e);
			throw new ConfigurationException("unexpected", e);
		}
		checkSimulator();
		simulator.configure();
		LOGGER.info("simulator created");

		simulator.addTickListener(this);

		uiMode = createUserInterface();
	}

	/**
	 * Access the simulator from the subclasses. Method returns simulator only
	 * after calling {@link ScenarioController#initialize()}.
	 * @return simulator or <code>null</code>
	 */
	public Simulator getSimulator() {
		return simulator;
	}

	private static Set<Enum<?>> merge(Set<Enum<?>> initialSet, Enum<?>[]... enums) {
		checkArgument(initialSet != null, "initial set can not be null");
		if (enums == null) {
			throw new IllegalArgumentException("enums can not be null");
		}
		Set<Enum<?>> mergedSet = newHashSet(initialSet);
		for (Enum<?>[] e : enums) {
			mergedSet.addAll(asList(e));
		}
		return mergedSet;
	}

	private final void checkSimulator() throws ConfigurationException {
		if (simulator == null) {
			throw new ConfigurationException("use createSimulator() to define simulator");
		}
	}

	public EventAPI getEventAPI() {
		return disp.getEventAPI();
	}

	/**
	 * Create simulator that will run the scenario.
	 * 
	 * @postcondition simulator != null && simulator not configured
	 * @return simulator
	 * @throws Exception
	 */
	protected abstract Simulator createSimulator() throws Exception;

	/**
	 * Create the user interface. By default method is empty and disables uiMode
	 * 
	 * @precondition simulator != null and simulator is configured
	 * @return uiMode. should be <code>true</code> when user interface was
	 *         created.
	 */
	protected boolean createUserInterface() {
		return false;
	}

	public void stop() {
		if (!uiMode) {
			simulator.removeTickListener(this);
			simulator.stop();
		}
	}

	public void start() throws ConfigurationException {
		checkSimulator();
		if (!uiMode) {
			new Thread() {
				@Override
				public void run() {
					simulator.start();
				}
			}.start();
		}
	}

	/**
	 * Returns true if all events of this scenario have been dispatched.
	 * 
	 * @return
	 */
	public boolean isScenarioFinished() {
		return scenario.peek() == null;
	}

	@Override
	final public void tick(long currentTime, long timeStep) {
		if (!uiMode && ticks == 0) {
			LOGGER.info("scenario finished at virtual time:" + currentTime);
			simulator.stop();
		}
		if (LOGGER.isDebugEnabled() && ticks >= 0) {
			LOGGER.debug("ticks to end: " + ticks);
		}
		if (ticks > -1) {
			ticks--;
		}
		TimedEvent e = null;
		while ((e = scenario.peek()) != null && e.time <= currentTime) {
			scenario.poll();
			if (status == null) {
				LOGGER.info("scenario started at virtual time:" + currentTime);
				status = Type.SCENARIO_STARTED;
				disp.dispatchEvent(new Event(status, this));
			}
			e.setIssuer(this);
			disp.dispatchEvent(e);
		}

		if (e == null && status != Type.SCENARIO_FINISHED) {
			LOGGER.info("scenario finished at virtual time:" + currentTime);
			status = Type.SCENARIO_FINISHED;
			simulator.removeTickListener(this);
			disp.dispatchEvent(new Event(status, this));
		}

	}

	@Override
	public void afterTick(long currentTime, long timeStep) {
		// not needed
	}

	@Override
	public void handleEvent(Event e) {
		if (e.getEventType() instanceof StandardType) {
			boolean handled = handleStandard(e);
			if (handled) {
				return;
			}
		}
		if (!handleCustomEvent(e)) {
			LOGGER.warn("event not handled: " + e.toString());
			throw new IllegalArgumentException("event not handled: " + e.toString());
		}
	}

	/**
	 * Can be used to handle additional events not supported by default. Default
	 * implementation lead to the {@link IllegalArgumentException} during event
	 * handling {@link ScenarioController#handleEvent(Event)}
	 * @param e
	 * @return <code>false</code> by default.
	 */
	protected boolean handleCustomEvent(Event e) {
		return false;
	}

	private boolean handleStandard(Event e) {
		StandardType eT = (StandardType) e.getEventType();
		switch (eT) {
		case ADD_PACKAGE:
			return handleAddPackage(e);
		case REMOVE_PACKAGE:
			return handleRemovePackage(e);
		case ADD_TRUCK:
			return handleAddTruck(e);
		case REMOVE_TRUCK:
			return handleRemoveTruck(e);
		default:
			return false;
		}
	}

	protected boolean handleRemoveTruck(Event e) {
		return false;
	}

	protected boolean handleAddTruck(Event e) {
		return false;
	}

	protected boolean handleRemovePackage(Event e) {
		return false;
	}

	protected boolean handleAddPackage(Event e) {
		return false;
	}
}
