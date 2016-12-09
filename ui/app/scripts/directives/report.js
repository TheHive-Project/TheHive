(function () {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('report', function ($templateRequest, $q, $compile) {
            function updateReport(a, b, scope) {
                if (!angular.isDefined(scope.content) || !angular.isDefined(scope.name)) {
                    scope.element.html('');
                    return;
                }

                // find report template
                $templateRequest('/api/connector/cortex/report/template/content/' + scope.name + '/' + scope.flavor, true)
                    .then(function (tmpl) {
                        scope.element.html($compile(tmpl)(scope));
                    }, function (response) {
                        $templateRequest('/views/reports/' + scope.flavor + '.html', true)
                            .then(function (tmpl) {
                                scope.element.html($compile(tmpl)(scope));
                            });
                    })
            }
            return {
                restrict: 'E',
                replace: true,
                link: function (scope, element) {
                    scope.element = element;
                    scope.$watchGroup(['name', 'content', 'status'], updateReport);
                },
                scope: {
                    'name': '=',
                    'artifact': '=',
                    'flavor': '@',
                    'status': '=',
                    'content': '='
                }
            };
        });
})();
