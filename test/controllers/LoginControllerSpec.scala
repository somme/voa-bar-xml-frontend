/*
 * Copyright 2018 HM Revenue & Customs
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

package controllers

import play.api.data.Form
import play.api.libs.json.Json
import uk.gov.hmrc.http.cache.client.CacheMap
import utils.FakeNavigator
import connectors.{FakeDataCacheConnector, LoginConnector}
import controllers.actions._
import play.api.test.Helpers._
import forms.LoginFormProvider
import identifiers.LoginId
import models.{Login, NormalMode}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import views.html.login

import scala.concurrent.Future
import scala.util.{Failure, Success}

class LoginControllerSpec extends ControllerSpecBase with MockitoSugar {

  def onwardRoute = routes.LoginController.onPageLoad(NormalMode)

  val formProvider = new LoginFormProvider()
  val form = formProvider()

  val loginConnector = mock[LoginConnector]
  when(loginConnector.send(any[Login])) thenReturn Future.successful(Success(200))

  val loginConnectorF = mock[LoginConnector]
  when(loginConnectorF.send(any[Login])) thenReturn Future.successful(Failure(new RuntimeException("Received exception from upstream service")))

  def controller(connector: LoginConnector, dataRetrievalAction: DataRetrievalAction = getEmptyCacheMap) =
    new LoginController(frontendAppConfig, messagesApi, FakeDataCacheConnector, new FakeNavigator(desiredRoute = onwardRoute),
      dataRetrievalAction, new DataRequiredActionImpl, formProvider, connector)

  def viewAsString(form: Form[_] = form) = login(frontendAppConfig, form, NormalMode)(fakeRequest, messages).toString

  "Login Controller" must {

    "return OK and the correct view for a GET" in {
      val result = controller(loginConnector).onPageLoad(NormalMode)(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) mustBe viewAsString()
    }

    "populate the view correctly on a GET when the question has previously been answered" in {
      val validData = Map(LoginId.toString -> Json.toJson(Login("username", "password")))
      val getRelevantData = new FakeDataRetrievalAction(Some(CacheMap(cacheMapId, validData)))

      val result = controller(loginConnector, getRelevantData).onPageLoad(NormalMode)(fakeRequest)

      contentAsString(result) mustBe viewAsString(form.fill(Login("username", "")))
    }

    "redirect to the next page when valid data is submitted" in {
      val postRequest = fakeRequest.withFormUrlEncodedBody(("username", "value 1"), ("password", "value 2"))

      val result = controller(loginConnector).onSubmit(NormalMode)(postRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(onwardRoute.url)
    }

    "return a Bad Request and errors when invalid data is submitted" in {
      val postRequest = fakeRequest.withFormUrlEncodedBody(("value", "invalid value"))
      val boundForm = form.bind(Map("value" -> "invalid value"))

      val result = controller(loginConnector).onSubmit(NormalMode)(postRequest)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustBe viewAsString(boundForm)
    }

    "return a Bad Request and errors when the backend service call fails" in {
      val postRequest = fakeRequest.withFormUrlEncodedBody(("username", "value 1"), ("password", "value 2"))
      val boundForm = form.bind(Map("username" -> "value 1", "password" -> "value2"))

      intercept[Exception] {
        val result = controller(loginConnectorF).onSubmit(NormalMode)(postRequest)
        status(result) mustBe BAD_REQUEST
        contentAsString(result) mustBe viewAsString(boundForm)
        redirectLocation(result) mustBe Some(onwardRoute.url)
      }
    }

  }
}
