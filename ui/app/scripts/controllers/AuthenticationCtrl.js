/**
 * Controller for login modal page2
 */
(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('AuthenticationCtrl', function($scope, $state, $location, $uibModalStack, $stateParams, AuthenticationSrv, NotificationSrv, appConfig) {
            $scope.params = {};
            $scope.ssoLogingIn = false;

            $uibModalStack.dismissAll();

            $scope.ssoLogin = function (code) {
                $scope.ssoLogingIn = true;
                AuthenticationSrv.ssoLogin(code)
                    .then(function(response) {
                        var redirectLocation = response.headers().location;
                        if(angular.isDefined(redirectLocation)) {
                            window.location = redirectLocation;
                        } else {
                            $location.search('code', null);
                            $state.go('app.cases');
                        }
                    })
                    .catch(function(err) {
                        if (err.status === 520) {
                            NotificationSrv.error('AuthenticationCtrl', err.data, err.status);
                        } else {
                            NotificationSrv.log(err.data.message, 'error');
                        }
                        $scope.ssoLogingIn = false;
                        $location.url($location.path());
                    });
            };

            $scope.ssoEnabled = function() {
                return appConfig.config.authType.indexOf("oauth2") !== -1;
            };


            $scope.login = function() {
                $scope.params.username = $scope.params.username.toLowerCase();
                AuthenticationSrv.login($scope.params.username, $scope.params.password)
                  .then(function() {
                      $state.go('app.cases');
                  })
                  .catch(function(err) {
                    if (err.status === 520) {
                        NotificationSrv.error('AuthenticationCtrl', err.data.message, err.status);
                    } else {
                        NotificationSrv.log(err.data.message, 'error');
                    }
                });
            };

            var code = $location.search().code;
            if(angular.isDefined(code) || (appConfig.config.ssoAutoLogin && !$stateParams.disableSsoAutoLogin)) {
                $scope.ssoLogin(code);
            }
        });
})();
