/**
 * Controller for login modal page2
 */
(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('AuthenticationCtrl', function($scope, $state, $location, $uibModalStack, $stateParams, AuthenticationSrv, NotificationSrv, UtilsSrv, UrlParser, appConfig) {
            $scope.params = {};

            $uibModalStack.dismissAll();

            $scope.ssoLogin = function (code) {
                AuthenticationSrv.ssoLogin(code, function(data, status, headers) {
                    var redirectLocation = headers().location;
                    if(angular.isDefined(redirectLocation)) {
                        window.location = redirectLocation;
                    } else {
                        $state.go('app.cases');
                    }
                }, function(data, status) {
                    if (status === 520) {
                        NotificationSrv.error('AuthenticationCtrl', data, status);
                    } else {
                        NotificationSrv.log(data.message, 'error');
                    }
                    $location.url($location.path());
                });
            };

            $scope.ssoEnabled = function() {
                return appConfig.config.authType.indexOf("oauth2") !== -1;
            };


            $scope.login = function() {
                $scope.params.username = angular.lowercase($scope.params.username);
                AuthenticationSrv.login($scope.params.username, $scope.params.password, function() {
                    $state.go('app.cases');
                }, function(data, status) {
                    if (status === 520) {
                        NotificationSrv.error('AuthenticationCtrl', data, status);
                    } else {
                        NotificationSrv.log(data.message, 'error');
                    }
                });
            };

            var code = UtilsSrv.extractQueryParam('code', UrlParser('query', $location.absUrl()));
            if(angular.isDefined(code) || $stateParams.autoLogin) {
                $scope.ssoLogin(code);
            }
        });
})();
