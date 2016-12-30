(function () {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('report', function ($templateRequest, $q, $compile) {
            function updateReport(a, b, scope) {
                if (!angular.isDefined(scope.content) || !angular.isDefined(scope.name)) {
                    scope.element.html('');
                    return;
                }

                var reportUrl = '/api/connector/cortex/report/template/content/' + scope.name + '/' + scope.reportType;

                // find report template
                $templateRequest(reportUrl, true)
                    .then(function (tmpl) {
                        scope.element.html($compile(tmpl)(scope));
                    }, function (/*response*/) {
                        $templateRequest('/views/reports/' + scope.reportType + '.html', true)
                            .then(function (tmpl) {
                                scope.element.html($compile(tmpl)(scope));
                            });
                    });
            }
            return {
                restrict: 'E',
                scope: {
                    name: '=',
                    artifact: '=',
                    reportType: '@',
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
