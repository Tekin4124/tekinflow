package com.tekinflow.demo.presentation.auth

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tekinflow.demo.testutil.FakeAuthApi
import com.tekinflow.demo.testutil.MainDispatcherRule
import com.tekinflow.demo.testutil.awaitItemWhere
import com.tekinflow.demo.testutil.errorHttpResponse
import com.tekinflow.demo.testutil.mockFcmTokenRegistrar
import com.tekinflow.demo.testutil.mockSessionManager
import com.tekinflow.demo.testutil.mockUserSession
import com.tekinflow.demo.testutil.sampleLoginResponse
import com.tekinflow.demo.testutil.sampleUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GirisViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `pending login message is shown on init`() = runTest {
        val viewModel = GirisViewModel(
            authApi = FakeAuthApi(),
            userSession = mockUserSession(null),
            sessionManager = mockSessionManager("Oturum süresi doldu"),
            fcmTokenRegistrar = mockFcmTokenRegistrar()
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.errorMessage).isEqualTo("Oturum süresi doldu")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login with valid credentials sets logged in user`() = runTest {
        val authApi = FakeAuthApi().apply {
            loginHandler = { request ->
                retrofit2.Response.success(
                    sampleLoginResponse(
                        userDto = com.tekinflow.demo.testutil.sampleUserDto(
                            username = request.username,
                            fullName = "Ayşe Yılmaz"
                        )
                    )
                )
            }
        }
        val userSession = mockUserSession(null)
        val viewModel = GirisViewModel(authApi, userSession, mockSessionManager(), mockFcmTokenRegistrar())

        viewModel.uiState.test {
            awaitItem()

            viewModel.onUsernameChange("  ayse  ")
            viewModel.onPasswordChange(" 1234 ")
            advanceUntilIdle()
            awaitItemWhere { it.username == "  ayse  " }
            awaitItemWhere { it.password == " 1234 " }

            viewModel.login()
            awaitItemWhere { it.isLoading }
            advanceTimeBy(350)
            advanceUntilIdle()

            val successState = awaitItemWhere { it.loggedInUser != null && !it.isLoading }
            assertThat(successState.loggedInUser?.username).isEqualTo("ayse")
            assertThat(successState.failedAttempts).isEqualTo(0)
            assertThat(authApi.loginRequests.single().username).isEqualTo("ayse")
            assertThat(authApi.loginRequests.single().password).isEqualTo("1234")
            assertThat(userSession.getUser()?.username).isEqualTo("ayse")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login with invalid credentials shows error`() = runTest {
        val authApi = FakeAuthApi().apply {
            loginHandler = { errorHttpResponse(401, "Yetkisiz") }
        }
        val viewModel = GirisViewModel(authApi, mockUserSession(null), mockSessionManager(), mockFcmTokenRegistrar())

        viewModel.uiState.test {
            awaitItem()
            viewModel.onUsernameChange("mehmet")
            viewModel.onPasswordChange("yanlis")
            advanceUntilIdle()
            awaitItemWhere { it.username == "mehmet" }
            awaitItemWhere { it.password == "yanlis" }

            viewModel.login()
            awaitItemWhere { it.isLoading }
            advanceTimeBy(350)
            advanceUntilIdle()

            val errorState = awaitItemWhere { !it.isLoading && it.errorMessage != null }
            assertThat(errorState.loggedInUser).isNull()
            assertThat(errorState.failedAttempts).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login with blank fields shows validation error`() = runTest {
        val authApi = FakeAuthApi()
        val viewModel = GirisViewModel(authApi, mockUserSession(null), mockSessionManager(), mockFcmTokenRegistrar())

        viewModel.uiState.test {
            awaitItem()
            viewModel.login()

            val validationState = awaitItemWhere { it.errorMessage != null }
            assertThat(validationState.isLoading).isFalse()
            assertThat(validationState.failedAttempts).isEqualTo(0)
            assertThat(authApi.loginRequests).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `three failed attempts trigger lockout`() = runTest {
        val authApi = FakeAuthApi().apply {
            loginHandler = { errorHttpResponse(401, "Yetkisiz") }
        }
        val viewModel = GirisViewModel(authApi, mockUserSession(null), mockSessionManager(), mockFcmTokenRegistrar())

        viewModel.uiState.test {
            awaitItem()
            viewModel.onUsernameChange("mehmet")
            viewModel.onPasswordChange("yanlis")
            advanceUntilIdle()
            awaitItemWhere { it.username == "mehmet" }
            awaitItemWhere { it.password == "yanlis" }

            repeat(3) {
                viewModel.login()
                awaitItemWhere { it.isLoading }
                advanceTimeBy(350)
                advanceUntilIdle()
            }

            val lockedState = awaitItemWhere { it.failedAttempts == 3 }
            assertThat(lockedState.failedAttempts).isEqualTo(3)
            assertThat(lockedState.lockoutSecondsRemaining).isAtLeast(29)
            assertThat(authApi.loginRequests).hasSize(3)
            cancelLockoutJob(viewModel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `during lockout login returns immediately`() = runTest {
        val authApi = FakeAuthApi()
        val viewModel = GirisViewModel(authApi, mockUserSession(null), mockSessionManager(), mockFcmTokenRegistrar())

        viewModel.uiState.test {
            awaitItem()
            setUiState(
                viewModel,
                LoginUiState(
                    username = "mehmet",
                    password = "yanlis",
                    failedAttempts = 3,
                    lockoutSecondsRemaining = 30
                )
            )
            awaitItemWhere { it.lockoutSecondsRemaining == 30 }

            authApi.loginHandler = { retrofit2.Response.success(sampleLoginResponse()) }
            val loginCountBefore = authApi.loginRequests.size
            viewModel.login()
            advanceUntilIdle()

            assertThat(authApi.loginRequests).hasSize(loginCountBefore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `after lockout expires login works again`() = runTest {
        val authApi = FakeAuthApi()
        val viewModel = GirisViewModel(authApi, mockUserSession(null), mockSessionManager(), mockFcmTokenRegistrar())

        viewModel.uiState.test {
            awaitItem()
            setUiState(
                viewModel,
                LoginUiState(
                    username = "mehmet",
                    password = "yanlis",
                    failedAttempts = 3,
                    lockoutSecondsRemaining = 30
                )
            )
            awaitItemWhere { it.lockoutSecondsRemaining == 30 }

            setLockoutEndTime(viewModel, System.currentTimeMillis() - 1_000L)
            startLockoutCountdown(viewModel)
            advanceUntilIdle()
            val unlockedState = awaitItemWhere { it.lockoutSecondsRemaining == 0 && it.failedAttempts == 0 }
            assertThat(unlockedState.errorMessage).isNull()

            authApi.loginHandler = { retrofit2.Response.success(sampleLoginResponse()) }
            viewModel.login()
            awaitItemWhere { it.isLoading }
            advanceTimeBy(350)
            advanceUntilIdle()

            val successState = awaitItemWhere { it.loggedInUser != null }
            assertThat(successState.loggedInUser?.username).isEqualTo(sampleUser().username)
            assertThat(authApi.loginRequests).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `successful login resets failed attempt counter`() = runTest {
        val authApi = FakeAuthApi().apply {
            loginHandler = { request ->
                if (request.password == "dogru") {
                    retrofit2.Response.success(sampleLoginResponse())
                } else {
                    errorHttpResponse(401, "Yetkisiz")
                }
            }
        }
        val viewModel = GirisViewModel(authApi, mockUserSession(null), mockSessionManager(), mockFcmTokenRegistrar())

        viewModel.uiState.test {
            awaitItem()
            viewModel.onUsernameChange("mehmet")
            viewModel.onPasswordChange("yanlis")
            advanceUntilIdle()
            awaitItemWhere { it.username == "mehmet" }
            awaitItemWhere { it.password == "yanlis" }

            viewModel.login()
            awaitItemWhere { it.isLoading }
            advanceTimeBy(350)
            advanceUntilIdle()
            val failedState = awaitItemWhere { it.failedAttempts == 1 }
            assertThat(failedState.loggedInUser).isNull()

            viewModel.onPasswordChange("dogru")
            advanceUntilIdle()
            awaitItemWhere { it.password == "dogru" }

            viewModel.login()
            awaitItemWhere { it.isLoading }
            advanceTimeBy(350)
            advanceUntilIdle()

            val successState = awaitItemWhere { it.loggedInUser != null }
            assertThat(successState.failedAttempts).isEqualTo(0)
            assertThat(successState.lockoutSecondsRemaining).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun setLockoutEndTime(viewModel: GirisViewModel, value: Long) {
        val field = GirisViewModel::class.java.getDeclaredField("lockoutEndTime")
        field.isAccessible = true
        field.setLong(viewModel, value)
    }

    private fun cancelLockoutJob(viewModel: GirisViewModel) {
        val field = GirisViewModel::class.java.getDeclaredField("lockoutJob")
        field.isAccessible = true
        (field.get(viewModel) as? kotlinx.coroutines.Job)?.cancel()
    }

    private fun startLockoutCountdown(viewModel: GirisViewModel) {
        val method = GirisViewModel::class.java.getDeclaredMethod("startLockoutCountdown")
        method.isAccessible = true
        method.invoke(viewModel)
    }

    private fun setUiState(viewModel: GirisViewModel, state: LoginUiState) {
        @Suppress("UNCHECKED_CAST")
        val stateFlow = GirisViewModel::class.java.getDeclaredField("_uiState").run {
            isAccessible = true
            get(viewModel) as MutableStateFlow<LoginUiState>
        }
        stateFlow.value = state
    }
}
