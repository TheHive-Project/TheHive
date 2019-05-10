(function() {
    'use strict';
    angular.module('theHiveControllers').controller('MigrationCtrl',
        function($rootScope, $scope, $http, $state, $timeout, $window, NotificationSrv, StreamSrv, UserSrv) {
            $rootScope.title = 'Database migration';
            $scope.migrationStatus = {};
            $scope.showUserForm = false;
            $scope.migrating = false;
            $scope.newUser = {};

            StreamSrv.init();

            StreamSrv.addListener({
                scope: $scope,
                rootId: 'any',
                objectType: 'migration',
                callback: function(events) {
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
                }
            });

            $scope.migrate = function() {
                $scope.migrating = true;
                $http.post('./api/maintenance/migrate', {}, {
                    timeout: 10 * 60 * 60 * 1000 // 10 minutes
                }).then(function(/*response*/) {
                    console.log('Migration started');
                }).catch(function(err) {
                    if (angular.isObject(err)) {
                        NotificationSrv.error('UserMgmtCtrl', err.data, err.status);
                    } else {
                      console.log("Migration timeout");
                    }
                });
            };

            $scope.createInitialUser = function() {
                console.log("createInitialUser");
                UserSrv.save({
                    'login': $scope.newUser.login.toLowerCase(),
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
