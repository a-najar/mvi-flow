package com.geniusforapp.mvi

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*

class MainViewModelTest {
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")
    private val viewModel = MainViewModel()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
    }


    @Test
    fun `given user name and password when action login should return ViewState with logged`() =
        runBlocking {
            viewModel.dispatch(Actions.LoginRequest("ahmadnajar@10@gmail.com", "123"))
            viewModel.state()
                .observeForever { Assert.assertEquals(it, State(isIdle = false, isLogged = true)) }
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // reset main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

}