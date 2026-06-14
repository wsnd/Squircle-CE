/*
 * Copyright Squircle CE contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blacksquircle.ui.feature.explorer.internal.api

import android.content.Context
import com.blacksquircle.ui.core.database.AppDatabase
import com.blacksquircle.ui.core.database.dao.workspace.WorkspaceDao
import com.blacksquircle.ui.core.provider.coroutine.DispatcherProvider
import com.blacksquircle.ui.core.settings.SettingsManager
import com.blacksquircle.ui.feature.explorer.api.factory.FilesystemFactory
import com.blacksquircle.ui.feature.explorer.api.interactor.ExplorerInteractor
import com.blacksquircle.ui.feature.explorer.api.manager.TaskManager
import com.blacksquircle.ui.feature.explorer.api.repository.ExplorerRepository
import com.blacksquircle.ui.feature.explorer.data.factory.FilesystemFactoryImpl
import com.blacksquircle.ui.feature.explorer.data.interactor.ExplorerInteractorImpl
import com.blacksquircle.ui.feature.explorer.data.manager.TaskManagerImpl
import com.blacksquircle.ui.feature.explorer.data.repository.ExplorerRepositoryImpl
import com.blacksquircle.ui.feature.explorer.data.workspace.DefaultWorkspaceSource
import com.blacksquircle.ui.feature.explorer.data.workspace.ServerWorkspaceSource
import com.blacksquircle.ui.feature.explorer.data.workspace.UserWorkspaceSource
import com.blacksquircle.ui.feature.explorer.ui.ExplorerEntryProvider
import com.blacksquircle.ui.feature.git.api.interactor.GitInteractor
import com.blacksquircle.ui.feature.servers.api.factory.ServerFactory
import com.blacksquircle.ui.feature.servers.api.interactor.ServerInteractor
import com.blacksquircle.ui.navigation.api.provider.EntryProvider
import com.scottyab.rootbeer.RootBeer
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
object ExplorerApiModule {

    @Provides
    @Singleton
    fun provideFilesystemFactory(
        serverFactory: ServerFactory,
        serverInteractor: ServerInteractor,
        context: Context,
    ): FilesystemFactory {
        return FilesystemFactoryImpl(
            serverFactory = serverFactory,
            serverInteractor = serverInteractor,
            context = context,
        )
    }

    @Provides
    @Singleton
    fun provideExplorerInteractor(
        explorerRepository: ExplorerRepository
    ): ExplorerInteractor {
        return ExplorerInteractorImpl(explorerRepository)
    }

    @Provides
    @Singleton
    fun provideExplorerRepository(
        dispatcherProvider: DispatcherProvider,
        settingsManager: SettingsManager,
        taskManager: TaskManager,
        gitInteractor: GitInteractor,
        filesystemFactory: FilesystemFactory,
        workspaceDao: WorkspaceDao,
        defaultWorkspaceSource: DefaultWorkspaceSource,
        userWorkspaceSource: UserWorkspaceSource,
        serverWorkspaceSource: ServerWorkspaceSource,
        context: Context,
    ): ExplorerRepository {
        return ExplorerRepositoryImpl(
            dispatcherProvider = dispatcherProvider,
            settingsManager = settingsManager,
            taskManager = taskManager,
            gitInteractor = gitInteractor,
            filesystemFactory = filesystemFactory,
            workspaceDao = workspaceDao,
            defaultWorkspaceSource = defaultWorkspaceSource,
            userWorkspaceSource = userWorkspaceSource,
            serverWorkspaceSource = serverWorkspaceSource,
            context = context,
        )
    }

    @Provides
    @Singleton
    fun provideTaskManager(dispatcherProvider: DispatcherProvider): TaskManager {
        return TaskManagerImpl(dispatcherProvider)
    }

    @Provides
    @Singleton
    fun provideDefaultWorkspaceSource(
        rootBeer: RootBeer,
        context: Context,
    ): DefaultWorkspaceSource {
        return DefaultWorkspaceSource(
            rootBeer = rootBeer,
            context = context
        )
    }

    @Provides
    @Singleton
    fun provideUserWorkspaceSource(workspaceDao: WorkspaceDao): UserWorkspaceSource {
        return UserWorkspaceSource(workspaceDao)
    }

    @Provides
    @Singleton
    fun provideServerWorkspaceSource(serverInteractor: ServerInteractor): ServerWorkspaceSource {
        return ServerWorkspaceSource(serverInteractor)
    }

    @Provides
    fun provideWorkspaceDao(appDatabase: AppDatabase): WorkspaceDao {
        return appDatabase.workspaceDao()
    }

    @Provides
    fun provideRootBeer(context: Context): RootBeer {
        return RootBeer(context)
    }

    @IntoSet
    @Provides
    fun provideExplorerEntryProvider(): EntryProvider {
        return ExplorerEntryProvider()
    }
}