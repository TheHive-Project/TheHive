(function () {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('report', function ($templateRequest, $q, $compile) {
            function updateReport(a, b, scope) {
                if (!angular.isDefined(scope.content) || !angular.isDefined(scope.name)) {
                    scope.element.html('');
                    return;
                }

                var reportUrl = '/api/connector/cortex/report/template/content/' + scope.name + '/' + scope.flavor;

                // find report template
                $templateRequest(reportUrl, true)
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
                scope: {
                    'name': '=',
                    'artifact': '=',
                    'flavor': '@',
                    'status': '=',
                    'content': '='
                },
                link: function (scope, element) {
                    scope.element = element;
                    scope.$watchGroup(['name', 'content', 'status'], updateReport);
                }
            };
        });
})();
