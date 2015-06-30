/*
 * Copyright (C) 2011-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.rinsim.central;

/**
 * Can be implemented by agents to obtain a {@link SimulationSolver} instance.
 * Requires a {@link SolverModel} to be present in the simulator.
 * @author Rinde van Lon
 */
public interface SolverUser {

  /**
   * Is called when the solver user is registered.
   * @param builder A builder for constructing a {@link SimulationSolver}.
   */
  void setSolverProvider(SimulationSolverBuilder builder);

}