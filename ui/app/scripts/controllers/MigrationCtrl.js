(function() {
    'use strict';
    angular.module('theHiveControllers').controller('MigrationCtrl',
        function($rootScope, $scope, $http, $state, $timeout, $window, AlertSrv, StreamSrv, UserSrv) {
            $rootScope.title = 'Database migration';
            $scope.migrationStatus = {};
            $scope.showUserForm = false;
            $scope.migrating = false;
            $scope.newUser = {};

            StreamSrv.init();
            StreamSrv.listen('any', 'migration', function(events) {
                angular.forEach(events, function(event) {
                    var tableName = event.base.tableName;

                    if (tableName === 'end') {
                        // check if there is at least one user registered
                        var users = UserSrv.query(function() {
                            if (users.length === 0) {
                                $scope.showUserForm = true;
                            } else {
                                $state.go('app.cases');
                            }
                        }, function() {
                            $state.go('app.cases');
                        });
                    }
                    var current = 0;
                    if (angular.isDefined($scope.migrationStatus[tableName])) {
                        current = $scope.migrationStatus[tableName].current;
                    }
                    if (event.base.current > current) {
                        $scope.migrationStatus[tableName] = event.base;
                    }
                });
            });

            $scope.migrate = function() {
                $scope.migrating = true;
                $http.post('./api/maintenance/migrate', {}, {
                    timeout: 10 * 60 * 60 * 1000 // 10 minutes
                }).success(function() {
                    console.log('Migration started');
                }).error(function(response) {
                    if (angular.isObject(response)) {
                        AlertSrv.error('UserMgmtCtrl', response.data, response.status);
                    } else {
                      console.log("Migration timeout");
                    }
                });
            };

            $scope.createInitialUser = function() {
                console.log("createInitialUser");
                UserSrv.save({
                    'login': angular.lowercase($scope.newUser.login),
                    'name': $scope.newUser.name,
                    'password': $scope.newUser.password,
                    'roles': ['read', 'write', 'admin']
                }, function() {
                    $state.go('app.cases');
                });
            };
        }
    );
})();
