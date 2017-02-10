angular.module('theHiveControllers', []);
angular.module('theHiveServices', []);
angular.module('theHiveFilters', []);
angular.module('theHiveDirectives', []);

angular.module('thehive', ['ngAnimate', 'ngMessages', 'ui.bootstrap', 'ui.router',
        'theHiveControllers', 'theHiveServices', 'theHiveFilters',
        'theHiveDirectives', 'yaru22.jsonHuman', 'timer', 'angularMoment', 'ngCsv', 'ngTagsInput', 'btford.markdown',
        'ngResource', 'ui-notification', 'angularjs-dropdown-multiselect', 'base64', 'angular-clipboard',
        'LocalStorageModule', 'angular-markdown-editor', 'hc.marked', 'hljs', 'ui.ace', 'angular-page-loader', 'naif.base64', 'images-resizer'
    ])
    .config(function($resourceProvider) {
        'use strict';

        $resourceProvider.defaults.stripTrailingSlashes = true;
    })
    .config(function($compileProvider) {
        'use strict';
        $compileProvider.debugInfoEnabled(false);
    })
    .config(function($stateProvider, $urlRouterProvider) {
        'use strict';

        $urlRouterProvider.otherwise('/cases');

        $stateProvider
            .state('login', {
                url: '/login',
                controller: 'AuthenticationCtrl',
                templateUrl: 'views/login.html',
                title: 'Login'
            })
            .state('live', {
                url: '/live',
                templateUrl: 'views/partials/live.html',
                controller: 'LiveCtrl',
                title: 'Live feed'
            })
            .state('maintenance', {
                url: '/maintenance',
                templateUrl: 'views/maintenance.html',
                controller: 'MigrationCtrl',
                title: 'Database migration'
            })
            .state('app', {
                url: '/',
                abstract: true,
                templateUrl: 'views/app.html',
                controller: 'RootCtrl',
                resolve: {
                    appConfig: function(VersionSrv) {
                        return VersionSrv.get();
                    }
                }
            })
            .state('app.main', {
                url: 'main/{viewId}',
                params: {
                    viewId: 'currentcases'
                },
                templateUrl: 'views/app.main.html',
                controller: 'MainPageCtrl'
            })
            .state('app.cases', {
                url: 'cases',
                templateUrl: 'views/partials/case/case.list.html',
                controller: 'CaseListCtrl',
                controllerAs: '$vm',
                title: 'Cases'
            })
            .state('app.search', {
                url: 'search?q',
                templateUrl: 'views/partials/search/list.html',
                controller: 'SearchCtrl',
                title: 'Search'
            })
            .state('app.settings', {
                url: 'settings',
                templateUrl: 'views/partials/personal-settings.html',
                controller: 'SettingsCtrl',
                title: 'Personal settings',
                resolve: {
                    appConfig: function(VersionSrv) {
                        return VersionSrv.get();
                    }
                }
            })
            .state('app.statistics', {
                url: 'statistics',
                templateUrl: 'views/partials/statistics.html',
                controller: 'StatisticsCtrl',
                title: 'Statistics'
            })
            .state('app.administration', {
                abstract: true,
                url: 'administration',
                template: '<ui-view/>'
            })
            .state('app.administration.users', {
                url: '/users',
                templateUrl: 'views/partials/admin/users.html',
                controller: 'AdminUsersCtrl',
                title: 'Users administration',
                resolve: {
                    appConfig: function(VersionSrv) {
                        return VersionSrv.get();
                    }
                }
            })
            .state('app.administration.case-templates', {
                url: '/case-templates',
                templateUrl: 'views/partials/admin/case-templates.html',
                controller: 'AdminCaseTemplatesCtrl',
                title: 'Templates administration'
            })
            .state('app.administration.report-templates', {
                url: '/report-templates',
                templateUrl: 'views/partials/admin/report-templates.html',
                controller: 'AdminReportTemplatesCtrl',
                controllerAs: 'vm',
                title: 'Report templates administration'
            })
            .state('app.administration.metrics', {
                url: '/metrics',
                templateUrl: 'views/partials/admin/metrics.html',
                controller: 'AdminMetricsCtrl',
                title: 'Metrics administration'
            })
            .state('app.administration.observables', {
                url: '/observables',
                templateUrl: 'views/partials/admin/observables.html',
                controller: 'AdminObservablesCtrl',
                title: 'Observable administration'
            })
            .state('app.case', {
                abstract: true,
                url: 'case/{caseId}',
                templateUrl: 'views/app.case.html',
                controller: 'CaseMainCtrl',
                title: 'Case',
                resolve: {
                    caze: function($q, $rootScope, $stateParams, CaseSrv, AlertSrv) {
                        var deferred = $q.defer();

                        CaseSrv.get({
                            'caseId': $stateParams.caseId,
                            'nstats': true
                        }, function(data) {

                            deferred.resolve(data);

                        }, function(response) {
                            deferred.reject(response);

                            AlertSrv.error('CaseMainCtrl', response.data, response.status);
                        });

                        return deferred.promise;
                    }
                }
            })
            .state('app.case.details', {
                url: '/details',
                templateUrl: 'views/partials/case/case.details.html',
                controller: 'CaseDetailsCtrl',
                data: {
                    tab: 'details'
                }
            })
            .state('app.case.tasks', {
                url: '/tasks',
                templateUrl: 'views/partials/case/case.tasks.html',
                controller: 'CaseTasksCtrl',
                data: {
                    tab: 'tasks'
                }
            })
            .state('app.case.links', {
                url: '/links',
                templateUrl: 'views/partials/case/case.links.html',
                controller: 'CaseLinksCtrl'
            })
            .state('app.case.tasks-item', {
                url: '/tasks/{itemId}',
                templateUrl: 'views/partials/case/case.tasks.item.html',
                controller: 'CaseTasksItemCtrl',
                resolve: {
                    task: function($q, $stateParams, CaseTaskSrv, AlertSrv) {
                        var deferred = $q.defer();

                        CaseTaskSrv.get({
                            'taskId': $stateParams.itemId
                        }, function(data) {
                            deferred.resolve(data);
                        }, function(response) {
                            deferred.reject(response);
                            AlertSrv.error('taskDetails', response.data, response.status);
                        });

                        return deferred.promise;
                    }
                }
            })
            .state('app.case.observables', {
                url: '/observables',
                templateUrl: 'views/partials/case/case.observables.html',
                controller: 'CaseObservablesCtrl',
                data: {
                    tab: 'observables'
                }
            })
            .state('app.case.observables-item', {
                url: '/observables/{itemId}',
                templateUrl: 'views/partials/case/case.observables.item.html',
                controller: 'CaseObservablesItemCtrl',
                resolve: {
                    appConfig: function(VersionSrv) {
                        return VersionSrv.get();
                    }
                }
            })
            .state('app.misp-list', {
                url: 'misp/list',
                templateUrl: 'views/partials/misp/list.html',
                controller: 'MispListCtrl',
                controllerAs: 'misp'
            });
    })
    .config(function($httpProvider) {
        'use strict';

        $httpProvider.interceptors.push(function($rootScope, $q) {
            var isApiCall = function(url) {
                return url && url.startsWith('./api') && !url.startsWith('./api/stream');
            };

            return {
                request: function(config) {
                    if (isApiCall(config.url)) {
                        $rootScope.async += 1;
                    }
                    return config;
                },
                response: function(response) {
                    if (isApiCall(response.config.url)) {
                        $rootScope.async -= 1;
                    }
                    return response;
                },
                responseError: function(rejection) {
                    if (isApiCall(rejection.config.url)) {
                        $rootScope.async -= 1;
                    }
                    return $q.reject(rejection);
                }
            };
        });
    })
    .config(function(localStorageServiceProvider) {
        'use strict';

        localStorageServiceProvider
            .setPrefix('th')
            .setStorageType('localStorage')
            .setNotify(false, false);
    })
    .config(function(NotificationProvider) {
        'use strict';

        NotificationProvider.setOptions({
            delay: 10000,
            startTop: 20,
            startRight: 10,
            verticalSpacing: 20,
            horizontalSpacing: 20,
            positionX: 'left',
            positionY: 'top'
        });
    })
    .config(['markedProvider', 'hljsServiceProvider', function(markedProvider, hljsServiceProvider) {
        'use strict';

        // marked config
        markedProvider.setOptions({
            gfm: true,
            tables: true,
            sanitize: true,
            highlight: function(code, lang) {
                if (lang) {
                    return hljs.highlight(lang, code, true).value;
                } else {
                    return hljs.highlightAuto(code).value;
                }
            }
        });

        // highlight config
        hljsServiceProvider.setOptions({
            // replace tab with 4 spaces
            tabReplace: '    '
        });
    }])
    .run(function($rootScope) {
        'use strict';
        $rootScope.async = 0;

        $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams) {
            if (_.isFunction(toState.title)) {
                $rootScope.title = toState.title(toParams);
            } else {
                $rootScope.title = toState.title;
            }
        });
    });
