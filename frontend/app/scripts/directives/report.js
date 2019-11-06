(function () {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('report', function ($templateRequest, $q, $compile) {
            function updateReport(a, b, scope) {
                if (!angular.isDefined(scope.content) || !angular.isDefined(scope.name)) {
                    scope.element.html('');
                    return;
                }

                var reportUrl = './api/connector/cortex/analyzer/template/content/' + scope.name;

                // find report template
                $templateRequest(reportUrl, true)
                    .then(function (tmpl) {
                        scope.element.append($compile(tmpl)(scope));
                    }, function (/*response*/) {
                        $templateRequest('views/reports/default.html', true)
                            .then(function (tmpl) {
                                scope.element.append($compile(tmpl)(scope));
                            });
                    });
            }

            return {
                restrict: 'E',
                scope: {
                    name: '=',
                    artifact: '=',
                    status: '=',
                    content: '=',
                    success: '='
                },
                link: function (scope, element) {
                    scope.element = element;
                    scope.$watchGroup(['name', 'content', 'status'], updateReport);
                }
            };
        });
})();
